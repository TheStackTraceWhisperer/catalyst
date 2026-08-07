package catalyst.server.lobby.transport;

import catalyst.common.network.MessageFrame;
import catalyst.common.network.WireCodec;
import catalyst.server.lobby.properties.ServerProperties;
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
import java.nio.charset.StandardCharsets;
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
        private Function<MessageFrame, MessageFrame> dispatcher = req -> MessageFrame.builder("ERROR").put("code","NOT_READY").put("message","Dispatcher not set").build();
        private final StringBuilder lineBuffer = new StringBuilder();

        RequestHandler(Function<MessageFrame, MessageFrame> dispatcher) {
            this.dispatcher = dispatcher;
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
                    MessageFrame response = dispatcher.apply(request);
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

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("QUIC stream error", cause);
            ctx.close();
        }
    }
}
