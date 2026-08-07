package catalyst.gateway.proxy;

import catalyst.common.network.GatewayFrame;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class ResponseStreamHandler extends ChannelInboundHandlerAdapter {
    private final CompletableFuture<GatewayFrame> future;


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof GatewayFrame frame) {
            if (future.complete(frame)) {
                ctx.close();
            }
        } else {
            future.completeExceptionally(new IllegalArgumentException("Expected GatewayFrame but got: " + msg.getClass().getName()));
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
