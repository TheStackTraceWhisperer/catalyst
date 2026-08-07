package catalyst.gateway.proxy;

import catalyst.common.network.ForyDecoder;
import catalyst.common.network.ForyEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicClientCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;

/** Internal QUIC client used by the gateway to forward Fory-encoded requests to backends. */
@Slf4j
public final class QuicGatewayClient implements AutoCloseable {
    public static final String PROTOCOL = "catalyst-1";
    private static final long REQUEST_TIMEOUT_MS = 5_000L;

    private final String host;
    private final int port;
    private final ReentrantLock connectionLock = new ReentrantLock();

    private EventLoopGroup group;
    private Channel udpChannel;
    private volatile QuicChannel quicChannel;

    public QuicGatewayClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /** Sends an already-framed Fory request without re-serializing it. */
    public Object request(byte[] rawFrame) throws IOException {
        try {
            ensureConnected();
            return sendRawOnStream(rawFrame);
        } catch (IOException e) {
            closeConnection();
            throw e;
        } catch (Exception e) {
            closeConnection();
            throw new IOException("QUIC backend request failed: " + e.getMessage(), e);
        }
    }

    /** Serializes and sends a request object through the Fory pipeline. */
    public Object request(Object request) throws IOException {
        try {
            ensureConnected();
            return sendObjectOnStream(request);
        } catch (IOException e) {
            closeConnection();
            throw e;
        } catch (Exception e) {
            closeConnection();
            throw new IOException("QUIC backend request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        closeConnection();
    }

    private void ensureConnected() throws Exception {
        if (quicChannel != null && quicChannel.isOpen()) {
            return;
        }

        connectionLock.lock();
        try {
            if (quicChannel != null && quicChannel.isOpen()) {
                return;
            }

            closeConnectionLocked();

            QuicSslContext sslContext = QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocols(PROTOCOL)
                .build();

            group = new NioEventLoopGroup(1);
            io.netty.channel.ChannelHandler codec = new QuicClientCodecBuilder()
                .sslEngineProvider(q -> sslContext.newEngine(q.alloc(), host, port))
                .maxIdleTimeout(60, TimeUnit.SECONDS)
                .initialMaxData(10_000_000)
                .initialMaxStreamDataBidirectionalLocal(1_000_000)
                .initialMaxStreamDataBidirectionalRemote(1_000_000)
                .initialMaxStreamsBidirectional(256)
                .build();

            udpChannel = new Bootstrap()
                .group(group)
                .channel(NioDatagramChannel.class)
                .handler(codec)
                .bind(0)
                .sync()
                .channel();

            quicChannel = QuicChannel.newBootstrap(udpChannel)
                .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
                    }
                })
                .remoteAddress(new InetSocketAddress(host, port))
                .connect()
                .get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } finally {
            connectionLock.unlock();
        }
    }

    private Object sendRawOnStream(byte[] rawFrame) throws Exception {
        return sendOnStream(
            stream -> stream.pipeline().addLast(new ForyDecoder()),
            Unpooled.wrappedBuffer(rawFrame)
        );
    }

    private Object sendObjectOnStream(Object request) throws Exception {
        return sendOnStream(
            stream -> stream.pipeline()
                .addLast(new ForyDecoder())
                .addLast(new ForyEncoder()),
            request
        );
    }

    private Object sendOnStream(StreamPipelineConfigurer pipelineConfigurer, Object outbound) throws Exception {
        CompletableFuture<Object> future = new CompletableFuture<>();

        QuicStreamChannel stream = quicChannel.createStream(
                QuicStreamType.BIDIRECTIONAL,
                new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
                        pipelineConfigurer.configure(ch);
                        ch.pipeline().addLast(new ResponseStreamHandler(future));
                    }
                })
            .sync()
            .getNow();

        stream.writeAndFlush(outbound).addListener(f -> {
            if (!f.isSuccess()) {
                future.completeExceptionally(f.cause());
                stream.close();
                return;
            }
            stream.shutdownOutput();
        });

        try {
            return future.get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            stream.close();
            throw new IOException("Request timed out to backend");
        }
    }

    private void closeConnection() {
        connectionLock.lock();
        try {
            closeConnectionLocked();
        } finally {
            connectionLock.unlock();
        }
    }

    private void closeConnectionLocked() {
        try {
            if (quicChannel != null) {
                quicChannel.close().sync();
                quicChannel = null;
            }
            if (udpChannel != null) {
                udpChannel.close().sync();
                udpChannel = null;
            }
            if (group != null) {
                group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
                group = null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class ResponseStreamHandler extends ChannelInboundHandlerAdapter {
        private final CompletableFuture<Object> future;

        ResponseStreamHandler(CompletableFuture<Object> future) {
            this.future = future;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (future.complete(msg)) {
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

    @FunctionalInterface
    private interface StreamPipelineConfigurer {
        void configure(QuicStreamChannel stream);
    }
}
