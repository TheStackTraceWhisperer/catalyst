package catalyst.server.lobby.transport;

import catalyst.server.lobby.properties.ServerProperties;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
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
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import catalyst.common.network.ForyDecoder;
import catalyst.common.network.ForyEncoder;
import catalyst.common.network.GatewayFrameDecoder;
import catalyst.common.network.GatewayFrameEncoder;

@Slf4j
@Singleton
@RequiredArgsConstructor
public final class QuicServerTransport {
    static final String PROTOCOL = "catalyst-1";

    private final ServerProperties props;
    private Function<Object, Object> dispatcher = req -> {
        log.error("Dispatcher not set for request: {}", req.getClass().getSimpleName());
        return null;
    };
    private EventLoopGroup group;
    private Channel bindChannel;

    public void setDispatcher(Function<Object, Object> dispatcher) {
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
                    ch.pipeline()
                        .addLast(new GatewayFrameDecoder())
                        .addLast(new GatewayFrameEncoder())
                        .addLast(new ForyDecoder())
                        .addLast(new ForyEncoder())
                        .addLast(new RequestHandler(dispatcher));
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
        private final Function<Object, Object> dispatcher;

        RequestHandler(Function<Object, Object> dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            try {
                // msg is already a decoded domain object (e.g., LoginRequest)
                log.debug("Received request: {}", msg.getClass().getSimpleName());
                
                Object response = dispatcher.apply(msg);
                
                if (response != null) {
                    if (response instanceof Object[] array) {
                        if (array.length > 0) {
                            for (int i = 0; i < array.length - 1; i++) {
                                ctx.write(array[i]);
                            }
                            ctx.writeAndFlush(array[array.length - 1]).addListener(f -> ((QuicStreamChannel) ctx.channel()).shutdownOutput());
                        } else {
                            ((QuicStreamChannel) ctx.channel()).shutdownOutput();
                        }
                    } else if (response instanceof Iterable<?> iterable) {
                        var list = new java.util.ArrayList<>();
                        iterable.forEach(list::add);
                        if (!list.isEmpty()) {
                            for (int i = 0; i < list.size() - 1; i++) {
                                ctx.write(list.get(i));
                            }
                            ctx.writeAndFlush(list.get(list.size() - 1)).addListener(f -> ((QuicStreamChannel) ctx.channel()).shutdownOutput());
                        } else {
                            ((QuicStreamChannel) ctx.channel()).shutdownOutput();
                        }
                    } else {
                        ctx.writeAndFlush(response).addListener(f -> {
                            ((QuicStreamChannel) ctx.channel()).shutdownOutput();
                        });
                    }
                } else {
                    log.warn("Dispatcher returned null for request: {}", msg.getClass().getSimpleName());
                    ctx.close();
                }
            } catch (Exception e) {
                log.error("Error processing request: {}", msg.getClass().getSimpleName(), e);
                ctx.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.warn("QUIC stream error", cause);
            ctx.close();
        }
    }
}
