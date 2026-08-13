package catalyst.gateway.proxy;

import catalyst.common.network.ForySerializer;
import catalyst.common.network.PacketType;
import catalyst.common.network.ServiceType;
import catalyst.server.common.network.ClientSession;
import catalyst.server.common.network.GatewayControlMessage;
import catalyst.server.common.network.GatewayFrame;
import catalyst.server.common.network.NetworkAttributes;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ResponseStreamHandler extends ChannelInboundHandlerAdapter {
    private final CompletableFuture<GatewayFrame> future;
    private final Consumer<GatewayControlMessage> controlCallback;

    public ResponseStreamHandler(CompletableFuture<GatewayFrame> future, Consumer<GatewayControlMessage> controlCallback) {
        this.future = future;
        this.controlCallback = controlCallback;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        try {
            GatewayFrame frame = null;

            if (msg instanceof GatewayFrame) {
                frame = (GatewayFrame) msg;
            } else if (msg instanceof ByteBuf buf) {
                if (buf.readableBytes() < 2) {
                    log.error("Received response payload with insufficient bytes for OpCode");
                    return;
                }
                int wireId = buf.readUnsignedShort();
                PacketType type = PacketType.fromWireId(wireId);
                ServiceType serviceType = (type != null) ? type.getTargetService() : ServiceType.CONTROL;

                ClientSession session = ctx.channel().attr(NetworkAttributes.SESSION_KEY).get();
                String sessionId = (session != null) ? String.valueOf(session.gatewaySessionId()) : "";

                byte[] payload = new byte[buf.readableBytes()];
                buf.readBytes(payload);

                frame = new GatewayFrame(serviceType, sessionId, payload);
            }

            if (frame != null) {
                if (frame.flag() == ServiceType.CONTROL) {
                    try {
                        Object controlObj = ForySerializer.deserialize(frame.payload());
                        if (controlObj instanceof GatewayControlMessage gcm) {
                            log.debug("Received GatewayControlMessage: command={}", gcm.command());
                            if (controlCallback != null) {
                                controlCallback.accept(gcm);
                            }
                        } else {
                            log.warn("Expected GatewayControlMessage but got: {}",
                              controlObj != null ? controlObj.getClass().getName() : "null");
                        }
                    } catch (Exception e) {
                        log.error("Failed to deserialize GatewayControlMessage", e);
                    }
                } else {
                    if (future.complete(frame)) {
                        ctx.close();
                    }
                }
            } else {
                future.completeExceptionally(new IllegalArgumentException(
                  "Expected GatewayFrame or ByteBuf but got: " + msg.getClass().getName()));
                ctx.close();
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (!future.isDone()) {
            future.completeExceptionally(new IOException("Stream closed before response received"));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        future.completeExceptionally(cause);
        ctx.close();
    }
}