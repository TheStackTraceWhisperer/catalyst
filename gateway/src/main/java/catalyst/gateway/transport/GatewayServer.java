package catalyst.gateway.transport;

import catalyst.common.dto.CharCreateResponse;
import catalyst.common.dto.CharDeleteResponse;
import catalyst.common.dto.CharListResponse;
import catalyst.common.dto.CharSelectResponse;
import catalyst.common.dto.LoginResponse;
import catalyst.common.dto.LogoutRequest;
import catalyst.common.dto.LogoutResponse;
import catalyst.common.dto.PingRequest;
import catalyst.common.dto.PingResponse;
import catalyst.common.dto.PlayResponse;
import catalyst.common.network.ForyDecoder;
import catalyst.common.network.ForyEncoder;
import catalyst.common.network.ResponseCode;
import catalyst.gateway.properties.GatewayProperties;
import catalyst.gateway.proxy.QuicGatewayClient;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
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
import io.netty.util.AttributeKey;
import jakarta.inject.Singleton;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public final class GatewayServer {
    private static final AttributeKey<Deque<PendingFrame>> PENDING_FRAMES =
        AttributeKey.valueOf("gateway.pendingFrames");

    private final GatewayProperties props;
    private final QuicGatewayClient loginClient;
    private final QuicGatewayClient lobbyClient;
    private final QuicGatewayClient worldClient;
    private final Map<String, BackendAddress> sessionRoutes = new ConcurrentHashMap<>();
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
                        .addLast(new RawFrameCaptureHandler())
                        .addLast(new ForyDecoder())
                        .addLast(new ForyEncoder())
                        .addLast(new RequestHandler(props, loginClient, lobbyClient, worldClient, sessionRoutes, dynamicWorldClients));
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
        sessionRoutes.clear();
    }

    private record BackendAddress(String host, int port) {
        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    private record PendingFrame(String routingKey, byte[] rawFrame) {
    }

    private static final class RawFrameCaptureHandler extends ChannelInboundHandlerAdapter {
        private byte[] captureBuffer = new byte[0];

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            ctx.channel().attr(PENDING_FRAMES).set(new ArrayDeque<>());
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof ByteBuf buf && buf.readableBytes() > 0) {
                byte[] chunk = new byte[buf.readableBytes()];
                buf.getBytes(buf.readerIndex(), chunk);
                captureBuffer = concat(captureBuffer, chunk);
                collectFrames(ctx);
            }
            ctx.fireChannelRead(msg);
        }

        private void collectFrames(ChannelHandlerContext ctx) {
            Deque<PendingFrame> frames = pendingFrames(ctx);
            while (true) {
                if (captureBuffer.length < 4) {
                    return;
                }

                int routingKeyLen = readInt(captureBuffer, 0);
                if (routingKeyLen < 0 || routingKeyLen > 1024) {
                    throw new IllegalArgumentException("Invalid routing key length: " + routingKeyLen);
                }

                int payloadLengthOffset = 4 + routingKeyLen;
                if (captureBuffer.length < payloadLengthOffset + 4) {
                    return;
                }

                String routingKey = new String(captureBuffer, 4, routingKeyLen, StandardCharsets.UTF_8);
                int payloadLen = readInt(captureBuffer, payloadLengthOffset);
                if (payloadLen < 0 || payloadLen > 10_000_000) {
                    throw new IllegalArgumentException("Invalid payload length: " + payloadLen);
                }

                int totalLen = payloadLengthOffset + 4 + payloadLen;
                if (captureBuffer.length < totalLen) {
                    return;
                }

                byte[] rawFrame = new byte[totalLen];
                System.arraycopy(captureBuffer, 0, rawFrame, 0, totalLen);
                frames.addLast(new PendingFrame(routingKey, rawFrame));

                int remaining = captureBuffer.length - totalLen;
                byte[] next = new byte[remaining];
                System.arraycopy(captureBuffer, totalLen, next, 0, remaining);
                captureBuffer = next;
            }
        }

        private static int readInt(byte[] bytes, int offset) {
            return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
        }
    }

    private static final class RequestHandler extends ChannelInboundHandlerAdapter {
        private final GatewayProperties props;
        private final QuicGatewayClient loginClient;
        private final QuicGatewayClient lobbyClient;
        private final QuicGatewayClient worldClient;
        private final Map<String, BackendAddress> sessionRoutes;
        private final Map<BackendAddress, QuicGatewayClient> dynamicWorldClients;

        RequestHandler(
            GatewayProperties props,
            QuicGatewayClient loginClient,
            QuicGatewayClient lobbyClient,
            QuicGatewayClient worldClient,
            Map<String, BackendAddress> sessionRoutes,
            Map<BackendAddress, QuicGatewayClient> dynamicWorldClients
        ) {
            this.props = props;
            this.loginClient = loginClient;
            this.lobbyClient = lobbyClient;
            this.worldClient = worldClient;
            this.sessionRoutes = sessionRoutes;
            this.dynamicWorldClients = dynamicWorldClients;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            PendingFrame frame = pendingFrames(ctx).pollFirst();
            if (frame == null) {
                log.warn("No captured raw frame available for {}", msg.getClass().getSimpleName());
                ctx.close();
                return;
            }

            Object response;
            try {
                response = routeAndForward(frame.routingKey(), msg, frame.rawFrame());
            } catch (Exception e) {
                log.error("Failed to forward request to backend (routingKey={})", frame.routingKey(), e);
                response = backendUnavailableResponse(frame.routingKey(), msg, e.getMessage());
            }
            if (response == null) {
                response = backendUnavailableResponse(frame.routingKey(), msg, "Backend returned no response");
            }

            ctx.writeAndFlush(response).addListener(f -> {
                if (!f.isSuccess()) {
                    log.warn("Failed to write gateway response", f.cause());
                }
                ((QuicStreamChannel) ctx.channel()).shutdownOutput();
            });
        }

        private Object routeAndForward(String routingKey, Object request, byte[] rawFrame) throws Exception {
            return switch (routingKey) {
                case "LoginRequest" -> loginClient.request(rawFrame);
                case "CharListRequest", "CharCreateRequest", "CharSelectRequest", "CharDeleteRequest" ->
                    lobbyClient.request(rawFrame);
                case "PlayRequest" -> {
                    Object response = lobbyClient.request(rawFrame);
                    captureSessionRoute(response);
                    yield response;
                }
                case "PingRequest", "LogoutRequest" -> {
                    String sessionId = extractSessionId(request);
                    Object response = resolveWorldClient(sessionId).request(rawFrame);
                    if ("LogoutRequest".equals(routingKey) && sessionId != null) {
                        sessionRoutes.remove(sessionId);
                    }
                    yield response;
                }
                default -> unsupportedResponse(routingKey, request);
            };
        }

        private void captureSessionRoute(Object response) {
            if (!(response instanceof PlayResponse playResponse)) {
                return;
            }
            if (playResponse.code() != ResponseCode.OK || playResponse.sessionId() == null || playResponse.sessionId().isBlank()) {
                return;
            }
            BackendAddress address = resolveWorldAddress(response);
            sessionRoutes.put(playResponse.sessionId(), address);
            log.info("Session {} routed to {}", playResponse.sessionId(), address);
        }

        private QuicGatewayClient resolveWorldClient(String sessionId) {
            BackendAddress address = sessionId == null ? null : sessionRoutes.get(sessionId);
            if (address == null) {
                return worldClient;
            }
            if (address.host().equals(props.getWorldhost()) && address.port() == props.getWorldport()) {
                return worldClient;
            }
            return dynamicWorldClients.computeIfAbsent(address, key -> {
                log.info("Opening new persistent internal connection to {}", key);
                return new QuicGatewayClient(key.host(), key.port());
            });
        }

        private BackendAddress resolveWorldAddress(Object response) {
            String address = extractWorldAddress(response);
            if (address == null || address.isBlank()) {
                return new BackendAddress(props.getWorldhost(), props.getWorldport());
            }
            String[] parts = address.split(":", 2);
            if (parts.length != 2) {
                log.warn("Invalid worldAddress '{}', using default world backend", address);
                return new BackendAddress(props.getWorldhost(), props.getWorldport());
            }
            try {
                return new BackendAddress(parts[0], Integer.parseInt(parts[1]));
            } catch (NumberFormatException e) {
                log.warn("Invalid worldAddress '{}', using default world backend", address);
                return new BackendAddress(props.getWorldhost(), props.getWorldport());
            }
        }

        private static String extractWorldAddress(Object response) {
            try {
                Object value = response.getClass().getMethod("worldAddress").invoke(response);
                return value instanceof String s ? s : null;
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        private static String extractSessionId(Object request) {
            return switch (request) {
                case PingRequest pingRequest -> pingRequest.sessionId();
                case LogoutRequest logoutRequest -> logoutRequest.sessionId();
                default -> null;
            };
        }

        private static Object backendUnavailableResponse(String routingKey, Object request, String detail) {
            return errorResponse(routingKey, request, "Backend connection failed: " + detail);
        }

        private static Object unsupportedResponse(String routingKey, Object request) {
            return errorResponse(routingKey, request, "Gateway does not route request: " + routingKey);
        }

        private static Object errorResponse(String routingKey, Object request, String message) {
            return switch (routingKey) {
                case "LoginRequest" -> new LoginResponse(ResponseCode.ERROR, message, null, -1);
                case "CharListRequest" -> new CharListResponse(ResponseCode.ERROR, List.of());
                case "CharCreateRequest" -> new CharCreateResponse(ResponseCode.ERROR, message, -1, null);
                case "CharSelectRequest" -> new CharSelectResponse(ResponseCode.ERROR, message, -1, null, 0, 0, 0f, 0f, 0f, 0f);
                case "CharDeleteRequest" -> new CharDeleteResponse(ResponseCode.ERROR, message, -1);
                case "PlayRequest" -> new PlayResponse(ResponseCode.ERROR, message, null, -1, -1, null, 0, 0, 5_000L, 0, 0f, 0f, 0f, 0f);
                case "PingRequest" -> new PingResponse("ERROR", extractSessionId(request), ResponseCode.ERROR, message);
                case "LogoutRequest" -> new LogoutResponse(extractSessionId(request), ResponseCode.ERROR, message);
                default -> new PingResponse("ERROR", extractSessionId(request), ResponseCode.ERROR, message);
            };
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("Gateway QUIC stream error", cause);
            ctx.close();
        }
    }

    private static Deque<PendingFrame> pendingFrames(ChannelHandlerContext ctx) {
        Deque<PendingFrame> frames = ctx.channel().attr(PENDING_FRAMES).get();
        if (frames == null) {
            frames = new ArrayDeque<>();
            ctx.channel().attr(PENDING_FRAMES).set(frames);
        }
        return frames;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
