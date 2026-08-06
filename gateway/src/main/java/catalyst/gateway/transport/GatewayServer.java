package catalyst.gateway.transport;

import catalyst.common.network.MessageFrame;
import catalyst.common.network.WireCodec;
import catalyst.gateway.config.GatewayProperties;
import catalyst.gateway.proxy.QuicGatewayClient;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.concurrent.Future;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public final class GatewayServer {
    private final GatewayProperties props;
    private final QuicGatewayClient loginClient;
    private final QuicGatewayClient lobbyClient;
    private final QuicGatewayClient worldClient;
    private final Map<String, String> sessionRoutes = new ConcurrentHashMap<>();

    private EventLoopGroup group;
    private Channel bindChannel;

    public GatewayServer(GatewayProperties props) {
        this.props = props;
        this.loginClient = new QuicGatewayClient(props.getLoginServiceHost(), props.getLoginServicePort());
        this.lobbyClient = new QuicGatewayClient(props.getLobbyServiceHost(), props.getLobbyServicePort());
        this.worldClient = new QuicGatewayClient(props.getWorldServiceHost(), props.getWorldServicePort());
    }

    public void start() throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();
        QuicSslContext sslContext = QuicSslContextBuilder.forServer(cert.key(), null, cert.cert())
            .applicationProtocols("catalyst-1")
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
                    ch.pipeline().addLast(new RequestHandler(props, loginClient, lobbyClient, worldClient, sessionRoutes));
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
        sessionRoutes.clear();
    }

    private static final class RequestHandler extends ChannelInboundHandlerAdapter {
        private final GatewayProperties props;
        private final QuicGatewayClient loginClient;
        private final QuicGatewayClient lobbyClient;
        private final QuicGatewayClient worldClient;
        private final Map<String, String> sessionRoutes;
        private final StringBuilder lineBuffer = new StringBuilder();

        RequestHandler(GatewayProperties props,
                       QuicGatewayClient loginClient,
                       QuicGatewayClient lobbyClient,
                       QuicGatewayClient worldClient,
                       Map<String, String> sessionRoutes) {
            this.props = props;
            this.loginClient = loginClient;
            this.lobbyClient = lobbyClient;
            this.worldClient = worldClient;
            this.sessionRoutes = sessionRoutes;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            try {
                lineBuffer.append(buf.toString(StandardCharsets.UTF_8));
                int newline;
                while ((newline = lineBuffer.indexOf("\n")) >= 0) {
                    String line = lineBuffer.substring(0, newline).trim();
                    lineBuffer.delete(0, newline + 1);
                    if (line.isBlank()) {
                        continue;
                    }
                    MessageFrame request = WireCodec.decode(line);
                    MessageFrame response;

                    try {
                        response = routeAndForward(request);
                    } catch (Exception e) {
                        log.error("Failed to forward request to backend: {}", request.type(), e);
                        response = MessageFrame.builder("ERROR")
                            .put("code", "BACKEND_UNAVAILABLE")
                            .put("message", "Backend connection failed: " + e.getMessage())
                            .build();
                    }

                    String encoded = WireCodec.encode(response.type(), response.fields()) + "\n";
                    ByteBuf out = Unpooled.copiedBuffer(encoded, StandardCharsets.UTF_8);
                    ctx.writeAndFlush(out).addListener((Future<Void> f) -> {
                        ((QuicStreamChannel) ctx.channel()).shutdownOutput();
                    });
                }
            } finally {
                buf.release();
            }
        }

        private MessageFrame routeAndForward(MessageFrame request) throws Exception {
            String type = request.type();
            switch (type) {
                case "LOGIN":
                    return loginClient.request(type, request.fields());
                
                case "CHAR_LIST":
                case "CHAR_CREATE":
                case "CHAR_DELETE":
                case "CHAR_SELECT":
                    return lobbyClient.request(type, request.fields());

                case "PLAY": {
                    MessageFrame response = worldClient.request(type, request.fields());
                    if ("PLAY_OK".equals(response.type()) || "OK".equals(response.fields().get("code"))) {
                        String sessionId = response.fields().get("sessionId");
                        if (sessionId != null) {
                            // Register session route
                            String worldAddr = props.getWorldServiceHost() + ":" + props.getWorldServicePort();
                            sessionRoutes.put(sessionId, worldAddr);
                        }
                    }
                    return response;
                }

                case "PING":
                case "LOGOUT": {
                    String sessionId = request.fields().get("sessionId");
                    String target = sessionId != null ? sessionRoutes.get(sessionId) : null;
                    MessageFrame response;
                    if (target != null) {
                        String[] parts = target.split(":");
                        String host = parts[0];
                        int port = Integer.parseInt(parts[1]);
                        if (host.equals(props.getWorldServiceHost()) && port == props.getWorldServicePort()) {
                            response = worldClient.request(type, request.fields());
                        } else {
                            try (QuicGatewayClient dynamicClient = new QuicGatewayClient(host, port)) {
                                response = dynamicClient.request(type, request.fields());
                            }
                        }
                    } else {
                        response = worldClient.request(type, request.fields());
                    }
                    if ("LOGOUT".equals(type) && sessionId != null) {
                        sessionRoutes.remove(sessionId);
                    }
                    return response;
                }

                default:
                    return MessageFrame.builder("ERROR")
                        .put("code", "UNSUPPORTED_REQUEST")
                        .put("message", "Gateway does not route: " + type)
                        .build();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("Gateway QUIC stream error", cause);
            ctx.close();
        }
    }
}
