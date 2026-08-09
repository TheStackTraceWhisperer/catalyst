package catalyst.gateway.proxy;

import catalyst.common.network.ForySerializer;
import catalyst.common.network.GatewayControlMessage;
import catalyst.common.network.GatewayFrame;
import catalyst.common.network.ServiceType;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles incoming packets from the backend stream.
 * Swallows CONTROL messages and triggers the state callback,
 * then completes the client future when the final game response arrives.
 */
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
        if (msg instanceof GatewayFrame frame) {
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
                // Do NOT close/complete; wait for the actual game DTO response frame
            } else {
                if (future.complete(frame)) {
                    ctx.close();
                }
            }
        } else {
            future.completeExceptionally(new IllegalArgumentException(
                "Expected GatewayFrame but got: " + msg.getClass().getName()));
            ctx.close();
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
