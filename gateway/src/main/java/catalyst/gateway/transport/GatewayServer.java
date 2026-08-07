package catalyst.gateway.transport;

import catalyst.common.network.MessageFrame;
import catalyst.common.network.Opcode;
import catalyst.common.network.WireCodec;
import catalyst.gateway.properties.GatewayProperties;
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

    // Connection pool for dynamic world servers to prevent EventLoop leaks
    private final Map<String, QuicGatewayClient> dynamicWorldClients = new ConcurrentHashMap<>();

    private EventLoopGroup group;
    private Channel bindChannel;

    public GatewayServer(GatewayProperties props) {
        this.props = props;
        this.loginClient = new QuicGatewayClient(props.getLoginhost(), props.getLoginport());
        this.lobbyClient = new QuicGatewayClient(props.getLobbyhost(), props.getLobbyport());
        this.worldClient = new QuicGatewayClient(props.getWorldhost(), props.getWorldport());
    }

    private volatile boolean bound = false;

    public boolean isBound() {
        return bound;
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
                    ch.pipeline().addLast(new RequestHandler(props, loginClient, lobbyClient, worldClient, sessionRoutes, dynamicWorldClients));
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

        // Clean up the dynamic pool
        dynamicWorldClients.values().forEach(QuicGatewayClient::close);
        dynamicWorldClients.clear();

        sessionRoutes.clear();
    }

    // ── Inner handler ────────────────────────────────────────────────────────

    private static final class RequestHandler extends ChannelInboundHandlerAdapter {
        private final GatewayProperties props;
        private final QuicGatewayClient loginClient;
        private final QuicGatewayClient lobbyClient;
        private final QuicGatewayClient worldClient;
        private final Map<String, String> sessionRoutes;
        private final Map<String, QuicGatewayClient> dynamicWorldClients;

        /**
         * Accumulates raw binary bytes until we have a full framed message.
         * Frame header = 5 bytes (1 opcode + 4 length), then N payload bytes.
         */
        private byte[] frameBuffer = new byte[0];

        RequestHandler(GatewayProperties props,
                       QuicGatewayClient loginClient,
                       QuicGatewayClient lobbyClient,
                       QuicGatewayClient worldClient,
                       Map<String, String> sessionRoutes,
                       Map<String, QuicGatewayClient> dynamicWorldClients) {
            this.props = props;
            this.loginClient = loginClient;
            this.lobbyClient = lobbyClient;
            this.worldClient = worldClient;
            this.sessionRoutes = sessionRoutes;
            this.dynamicWorldClients = dynamicWorldClients;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            try {
                // Append incoming bytes to our assembly buffer
                int readable = buf.readableBytes();
                byte[] chunk = new byte[readable];
                buf.readBytes(chunk);
                frameBuffer = concat(frameBuffer, chunk);

                // Process all complete frames in the buffer
                while (true) {
                    if (frameBuffer.length < WireCodec.PAYLOAD_OFFSET) break; // need header bytes
                    int totalLen = WireCodec.framedLength(frameBuffer);
                    if (frameBuffer.length < totalLen) break; // need more payload bytes

                    // Extract the complete frame
                    byte[] frame = new byte[totalLen];
                    System.arraycopy(frameBuffer, 0, frame, 0, totalLen);

                    // Trim consumed bytes from buffer
                    int remaining = frameBuffer.length - totalLen;
                    byte[] newBuf = new byte[remaining];
                    System.arraycopy(frameBuffer, totalLen, newBuf, 0, remaining);
                    frameBuffer = newBuf;

                    // Route by opcode — no full deserialization needed
                    Opcode opcode = WireCodec.peekOpcode(frame);
                    MessageFrame response;
                    try {
                        response = routeAndForward(opcode, frame);
                    } catch (Exception e) {
                        log.error("Failed to forward request to backend (opcode={})", opcode, e);
                        response = MessageFrame.builder("ERROR")
                            .put("code", "BACKEND_UNAVAILABLE")
                            .put("message", "Backend connection failed: " + e.getMessage())
                            .build();
                    }

                    byte[] encoded = WireCodec.encode(response);
                    ByteBuf out = Unpooled.wrappedBuffer(encoded);
                    ctx.writeAndFlush(out).addListener((Future<Void> f) -> {
                        ((QuicStreamChannel) ctx.channel()).shutdownOutput();
                    });
                }
            } finally {
                buf.release();
            }
        }

        /**
         * Routes the raw frame bytes to the correct backend using the opcode,
         * avoiding full deserialization for lobby/world routing decisions.
         * <p>
         * For PING and LOGOUT we must decode to read the sessionId for session-based routing.
         */
        private MessageFrame routeAndForward(Opcode opcode, byte[] rawFrame) throws Exception {
            return switch (opcode) {
                case LOGIN -> {
                    MessageFrame req = WireCodec.decode(rawFrame);
                    yield loginClient.request(rawFrame);
                }
                case CHAR_LIST, CHAR_CREATE, CHAR_DELETE, CHAR_SELECT -> {
                    yield lobbyClient.request(rawFrame);
                }
                case PLAY -> {
                    MessageFrame response = lobbyClient.request(rawFrame);
                    if ("PLAY_OK".equals(response.type())) {
                        String sessionId = response.fields().get("sessionId");
                        String worldAddress = response.fields().get("worldAddress");
                        if (worldAddress == null) {
                            worldAddress = props.getWorldhost() + ":" + props.getWorldport();
                        }
                        if (sessionId != null) {
                            sessionRoutes.put(sessionId, worldAddress);
                            log.info("Session {} routed to {}", sessionId, worldAddress);
                        }
                    }
                    yield response;
                }
                case PING, LOGOUT -> {
                    // Must decode to read sessionId for session-based routing
                    MessageFrame req = WireCodec.decode(rawFrame);
                    String sessionId = req.fields().get("sessionId");
                    String target = sessionId != null ? sessionRoutes.get(sessionId) : null;
                    MessageFrame response;

                    if (target != null) {
                        String[] parts = target.split(":");
                        String host = parts[0];
                        int port = Integer.parseInt(parts[1]);

                        if (host.equals(props.getWorldhost()) && port == props.getWorldport()) {
                            response = worldClient.request(rawFrame);
                        } else {
                            QuicGatewayClient targetClient = dynamicWorldClients.computeIfAbsent(
                                target, k -> {
                                    log.info("Opening new persistent internal connection to {}", target);
                                    return new QuicGatewayClient(host, port);
                                }
                            );
                            response = targetClient.request(rawFrame);
                        }
                    } else {
                        response = worldClient.request(rawFrame);
                    }

                    if (opcode == Opcode.LOGOUT && sessionId != null) {
                        sessionRoutes.remove(sessionId);
                    }
                    yield response;
                }
                default -> {
                    log.warn("Unroutable opcode received: {}", opcode);
                    yield MessageFrame.builder("ERROR")
                        .put("code", "UNSUPPORTED_REQUEST")
                        .put("message", "Gateway does not route opcode: " + opcode)
                        .build();
                }
            };
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("Gateway QUIC stream error", cause);
            ctx.close();
        }

        // ── Buffer helpers ───────────────────────────────────────────────────

        private static byte[] concat(byte[] a, byte[] b) {
            byte[] result = new byte[a.length + b.length];
            System.arraycopy(a, 0, result, 0, a.length);
            System.arraycopy(b, 0, result, a.length, b.length);
            return result;
        }
    }
}
