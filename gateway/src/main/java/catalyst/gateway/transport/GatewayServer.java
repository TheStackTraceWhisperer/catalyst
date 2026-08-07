package catalyst.gateway.transport;

import catalyst.common.network.GatewayFrame;
import catalyst.common.network.GatewayFrameDecoder;
import catalyst.common.network.GatewayFrameEncoder;
import catalyst.gateway.properties.GatewayProperties;
import catalyst.gateway.proxy.QuicGatewayClient;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.AttributeKey;
import jakarta.inject.Singleton;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public final class GatewayServer {

    private final GatewayProperties props;
    private final QuicGatewayClient loginClient;
    private final QuicGatewayClient lobbyClient;
    private final QuicGatewayClient worldClient;
    private final Map<BackendAddress, QuicGatewayClient> dynamicWorldClients = new ConcurrentHashMap<>();

    private EventLoopGroup group;
    private Channel bindChannel;
    private volatile boolean bound = false;

    public GatewayServer(GatewayProperties props) {
        this.props = props;
        this.loginClient = new QuicGatewayClient(props.getLoginhost(), props.getLoginport());
        this.lobbyClient = new QuicGatewayClient(props.getLobbyhost(), props.getLobbyport());
        this.worldClient = new QuicGatewayClient(props.getWorldhost(), props.getWorldport());
    }

    public boolean isBound() {
        return bound;
    }

    public void start() throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();
        QuicSslContext sslContext = QuicSslContextBuilder.forServer(cert.key(), null, cert.cert())
            .applicationProtocols(QuicGatewayClient.PROTOCOL)
            .build();

        group = new NioEventLoopGroup();
        io.netty.channel.ChannelHandler codec = new QuicServerCodecBuilder()
            .sslEngineProvider(q -> sslContext.newEngine(q.alloc()))
            .maxIdleTimeout(60, TimeUnit.SECONDS)
            .initialMaxData(10_000_000)
            .initialMaxStreamDataBidirectionalLocal(1_000_000)
            .initialMaxStreamDataBidirectionalRemote(1_000_000)
            .initialMaxStreamsBidirectional(256)
            .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
            .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                @Override
                protected void initChannel(QuicStreamChannel ch) {
                    ch.pipeline()
                        .addLast(new GatewayFrameDecoder())
                        .addLast(new GatewayFrameEncoder())
                        .addLast(new RequestHandler(props, loginClient, lobbyClient, worldClient, dynamicWorldClients));
                }
            })
            .build();

        bindChannel = new Bootstrap()
            .group(group)
            .channel(NioDatagramChannel.class)
            .handler(codec)
            .bind(new InetSocketAddress(props.getPort()))
            .sync()
            .channel();

        bound = true;
        log.info("Gateway Server bound on UDP port {}", props.getPort());
    }

    public void awaitShutdown() throws InterruptedException {
        if (bindChannel != null) {
            bindChannel.closeFuture().sync();
        }
    }

    public void stop() {
        if (bindChannel != null) {
            bindChannel.close();
        }
        if (group != null) {
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS);
        }
        loginClient.close();
        lobbyClient.close();
        worldClient.close();
        dynamicWorldClients.values().forEach(QuicGatewayClient::close);
        dynamicWorldClients.clear();
    }

    private record BackendAddress(String host, int port) {
        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    private static final class RequestHandler extends ChannelInboundHandlerAdapter {
        private static final AttributeKey<ConnectionState> STATE_KEY = AttributeKey.valueOf("gateway.state");
        private static final AttributeKey<QuicGatewayClient> WORLD_CLIENT_KEY = AttributeKey.valueOf("gateway.worldClient");

        private enum ConnectionState {
            UNAUTHENTICATED,
            AUTHENTICATED,
            PLAYING
        }

        private final GatewayProperties props;
        private final QuicGatewayClient loginClient;
        private final QuicGatewayClient lobbyClient;
        private final QuicGatewayClient worldClient;
        private final Map<BackendAddress, QuicGatewayClient> dynamicWorldClients;

        RequestHandler(
            GatewayProperties props,
            QuicGatewayClient loginClient,
            QuicGatewayClient lobbyClient,
            QuicGatewayClient worldClient,
            Map<BackendAddress, QuicGatewayClient> dynamicWorldClients
        ) {
            this.props = props;
            this.loginClient = loginClient;
            this.lobbyClient = lobbyClient;
            this.worldClient = worldClient;
            this.dynamicWorldClients = dynamicWorldClients;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof GatewayFrame requestFrame)) {
                log.warn("Expected GatewayFrame in RequestHandler, got {}", msg.getClass().getName());
                ctx.close();
                return;
            }

            // Get connection state from parent channel (since QuicStreamChannel's parent is the QuicChannel connection)
            Channel parentChannel = ctx.channel().parent();
            ConnectionState state = parentChannel.attr(STATE_KEY).get();
            if (state == null) {
                state = ConnectionState.UNAUTHENTICATED;
                parentChannel.attr(STATE_KEY).set(state);
            }

            QuicGatewayClient client = resolveTargetClient(parentChannel, state, requestFrame);
            if (client == null) {
                log.warn("No target client resolved for request flag={} in state={}", requestFrame.flag(), state);
                ctx.writeAndFlush(new GatewayFrame(GatewayFrame.FLAG_CONTROL, "error=unauthorized_route", new byte[0]))
                    .addListener(f -> ((QuicStreamChannel) ctx.channel()).shutdownOutput());
                return;
            }

            try {
                GatewayFrame responseFrame = client.request(requestFrame);
                handleStateTransitions(parentChannel, responseFrame);
                
                ctx.writeAndFlush(responseFrame).addListener(f -> {
                    if (!f.isSuccess()) {
                        log.warn("Failed to write gateway response", f.cause());
                    }
                    ((QuicStreamChannel) ctx.channel()).shutdownOutput();
                });
            } catch (Exception e) {
                log.error("Failed to forward request to backend", e);
                ctx.writeAndFlush(new GatewayFrame(GatewayFrame.FLAG_CONTROL, "error=backend_unavailable", new byte[0]))
                    .addListener(f -> ((QuicStreamChannel) ctx.channel()).shutdownOutput());
            }
        }

        private QuicGatewayClient resolveTargetClient(Channel parentChannel, ConnectionState state, GatewayFrame requestFrame) {
            // Enforcement rules based on connection state
            if (state == ConnectionState.UNAUTHENTICATED) {
                // Force all traffic to login server when unauthenticated
                return loginClient;
            }

            // Authenticated or Playing state
            byte flag = requestFrame.flag();
            if (flag == GatewayFrame.FLAG_LOGIN) {
                return loginClient;
            } else if (flag == GatewayFrame.FLAG_LOBBY) {
                return lobbyClient;
            } else if (flag == GatewayFrame.FLAG_WORLD) {
                if (state == ConnectionState.PLAYING) {
                    QuicGatewayClient world = parentChannel.attr(WORLD_CLIENT_KEY).get();
                    return world != null ? world : worldClient;
                }
            }
            return null;
        }

        private void handleStateTransitions(Channel parentChannel, GatewayFrame responseFrame) {
            String metadata = responseFrame.metadata();
            String status = getMetadataValue(metadata, "status");
            if (status == null) {
                return;
            }

            switch (status) {
                case "auth_success" -> {
                    log.info("Client authenticated, transitioning to AUTHENTICATED");
                    parentChannel.attr(STATE_KEY).set(ConnectionState.AUTHENTICATED);
                }
                case "play_success" -> {
                    String worldAddr = getMetadataValue(metadata, "worldAddress");
                    String sessionId = getMetadataValue(metadata, "sessionId");
                    log.info("Client play success, sessionId={}, transitioning to PLAYING", sessionId);

                    parentChannel.attr(STATE_KEY).set(ConnectionState.PLAYING);

                    BackendAddress address;
                    if (worldAddr == null || "DEFAULT".equals(worldAddr) || worldAddr.isBlank()) {
                        address = new BackendAddress(props.getWorldhost(), props.getWorldport());
                    } else {
                        String[] parts = worldAddr.split(":", 2);
                        if (parts.length == 2) {
                            try {
                                address = new BackendAddress(parts[0], Integer.parseInt(parts[1]));
                            } catch (NumberFormatException e) {
                                log.warn("Invalid worldAddress format '{}', using default", worldAddr);
                                address = new BackendAddress(props.getWorldhost(), props.getWorldport());
                            }
                        } else {
                            address = new BackendAddress(props.getWorldhost(), props.getWorldport());
                        }
                    }

                    // Resolve the specific world server client
                    QuicGatewayClient targetClient;
                    if (address.host().equals(props.getWorldhost()) && address.port() == props.getWorldport()) {
                        targetClient = worldClient;
                    } else {
                        targetClient = dynamicWorldClients.computeIfAbsent(address, key -> {
                            log.info("Opening new persistent internal connection to dynamic world backend: {}", key);
                            return new QuicGatewayClient(key.host(), key.port());
                        });
                    }
                    parentChannel.attr(WORLD_CLIENT_KEY).set(targetClient);
                }
            }
        }

        private static String getMetadataValue(String metadata, String key) {
            if (metadata == null || metadata.isEmpty()) {
                return null;
            }
            String[] parts = metadata.split(";");
            for (String part : parts) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2 && kv[0].trim().equals(key)) {
                    return kv[1].trim();
                }
            }
            return null;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("Gateway QUIC stream error", cause);
            ctx.close();
        }
    }
}
