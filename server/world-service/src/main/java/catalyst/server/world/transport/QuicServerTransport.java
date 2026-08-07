package catalyst.server.world.transport;

import catalyst.common.network.MessageFrame;
import catalyst.common.network.WireCodec;
import catalyst.server.world.properties.ServerProperties;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
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
import io.netty.util.concurrent.Future;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public final class QuicServerTransport {
    static final String PROTOCOL = "catalyst-1";

    private final ServerProperties props;
    private Function<MessageFrame, MessageFrame> dispatcher = req ->
        MessageFrame.builder("ERROR").put("code","NOT_READY").put("message","Dispatcher not set").build();
    private EventLoopGroup group;
    private Channel bindChannel;

    public void setDispatcher(Function<MessageFrame, MessageFrame> dispatcher) {
        this.dispatcher = dispatcher;
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
        ChannelHandler codec = new QuicServerCodecBuilder()
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
                    ch.pipeline().addLast(new RequestHandler(dispatcher));
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
        log.info("QUIC server bound on UDP port {}", props.getPort());
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
    }

    private static final class RequestHandler extends ChannelInboundHandlerAdapter {
        private final Function<MessageFrame, MessageFrame> dispatcher;
        /** Byte accumulation buffer for framed binary messages. */
        private byte[] frameBuffer = new byte[0];

        RequestHandler(Function<MessageFrame, MessageFrame> dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            try {
                int readable = buf.readableBytes();
                byte[] chunk = new byte[readable];
                buf.readBytes(chunk);
                frameBuffer = concat(frameBuffer, chunk);

                while (true) {
                    if (frameBuffer.length < WireCodec.PAYLOAD_OFFSET) break;
                    int totalLen = WireCodec.framedLength(frameBuffer);
                    if (frameBuffer.length < totalLen) break;

                    byte[] frame = new byte[totalLen];
                    System.arraycopy(frameBuffer, 0, frame, 0, totalLen);

                    int remaining = frameBuffer.length - totalLen;
                    byte[] newBuf = new byte[remaining];
                    System.arraycopy(frameBuffer, totalLen, newBuf, 0, remaining);
                    frameBuffer = newBuf;

                    MessageFrame request = WireCodec.decode(frame);
                    MessageFrame response = dispatcher.apply(request);
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

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("QUIC stream error", cause);
            ctx.close();
        }

        private static byte[] concat(byte[] a, byte[] b) {
            byte[] result = new byte[a.length + b.length];
            System.arraycopy(a, 0, result, 0, a.length);
            System.arraycopy(b, 0, result, a.length, b.length);
            return result;
        }
    }
}
