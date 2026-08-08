package catalyst.gateway.proxy;

import catalyst.common.network.GatewayFrame;
import catalyst.common.network.GatewayFrameDecoder;
import catalyst.common.network.GatewayFrameEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
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
import java.util.concurrent.locks.ReentrantLock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Internal client used by the gateway to forward GatewayFrame requests asynchronously to backends. */
@Slf4j
@RequiredArgsConstructor
public final class BackendClient implements AutoCloseable {
    public static final String PROTOCOL = "catalyst-1";
    private static final long REQUEST_TIMEOUT_MS = 5_000L;

    private final String host;
    private final int port;
    private final ReentrantLock connectionLock = new ReentrantLock();

    private EventLoopGroup group;
    private Channel udpChannel;
    private volatile QuicChannel quicChannel;

    /** Sends a GatewayFrame request asynchronously and returns a CompletableFuture containing the response. */
    public CompletableFuture<GatewayFrame> requestAsync(GatewayFrame frame, java.util.function.Consumer<catalyst.common.network.GatewayControlMessage> controlCallback) {
        CompletableFuture<GatewayFrame> future = new CompletableFuture<>();
        
        // Use a virtual thread to handle connection handshakes to avoid blocking Netty EventLoop
        Thread.ofVirtual().start(() -> {
            try {
                ensureConnected();
                sendOnStreamAsync(frame, future, controlCallback);
            } catch (Exception e) {
                closeConnection();
                future.completeExceptionally(new IOException("QUIC backend request failed: " + e.getMessage(), e));
            }
        });

        // Apply a timeout to the future
        future.orTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .exceptionally(ex -> {
                log.warn("Request timed out to backend {}:{}", host, port);
                return null;
            });

        return future;
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

    private void sendOnStreamAsync(GatewayFrame outbound, CompletableFuture<GatewayFrame> future, java.util.function.Consumer<catalyst.common.network.GatewayControlMessage> controlCallback) {
        quicChannel.createStream(
                QuicStreamType.BIDIRECTIONAL,
                new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
                        ch.pipeline()
                            .addLast(new GatewayFrameDecoder())
                            .addLast(new GatewayFrameEncoder())
                            .addLast(new ResponseStreamHandler(future, controlCallback));
                    }
                })
            .addListener(f -> {
                if (!f.isSuccess()) {
                    future.completeExceptionally(f.cause());
                    return;
                }
                QuicStreamChannel stream = (QuicStreamChannel) f.getNow();
                stream.writeAndFlush(outbound).addListener(writeFuture -> {
                    if (!writeFuture.isSuccess()) {
                        future.completeExceptionally(writeFuture.cause());
                        stream.close();
                        return;
                    }
                    stream.shutdownOutput();
                });
            });
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
}
