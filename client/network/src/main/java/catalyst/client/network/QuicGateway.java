package catalyst.client.network;

import catalyst.common.network.ForyDecoder;
import catalyst.common.network.ForyEncoder;
import catalyst.common.network.GatewayFrameDecoder;
import catalyst.common.network.GatewayFrameEncoder;
import catalyst.client.network.dispatch.ClientDispatcher;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicClientCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RequiredArgsConstructor
public final class QuicGateway implements AutoCloseable {
    static final String PROTOCOL = "catalyst-1";
    private static final long REQUEST_TIMEOUT_MS = 5_000L;

    private final ClientDispatcher clientDispatcher;
    private EventLoopGroup group;
    private Channel udpChannel;
    private QuicChannel quicChannel;
    private String connectedHost;
    private int connectedPort;

    synchronized <T> T request(String host, int port, Object request, Class<T> responseType) throws IOException {
        try {
            ensureConnected(host, port);
            return sendOnStream(request, responseType);
        } catch (IOException e) {
            closeConnection();
            throw e;
        } catch (Exception e) {
            closeConnection();
            throw new IOException("QUIC request failed: " + e.getMessage(), e);
        }
    }

    synchronized void sendAsync(String host, int port, Object request) {
        try {
            ensureConnected(host, port);
            quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
                        ch.pipeline()
                            .addLast(new GatewayFrameDecoder())
                            .addLast(new GatewayFrameEncoder())
                            .addLast(new ForyDecoder())
                            .addLast(new ForyEncoder())
                            .addLast(new SimpleChannelInboundHandler<Object>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
                                    clientDispatcher.enqueue(msg);
                                    ctx.close();
                                }
                            });
                    }
                })
            .addListener(future -> {
                if (future.isSuccess()) {
                    QuicStreamChannel stream = (QuicStreamChannel) future.getNow();
                    stream.writeAndFlush(request).addListener(writeFuture -> {
                        if (!writeFuture.isSuccess()) {
                            stream.close();
                        } else {
                            stream.shutdownOutput();
                        }
                    });
                } else {
                    log.error("Failed to create stream for async send", future.cause());
                }
            });
        } catch (Exception e) {
            log.error("Failed to send async request", e);
        }
    }

    @Override
    public synchronized void close() {
        closeConnection();
    }

    private void ensureConnected(String host, int port) throws Exception {
        if (quicChannel != null && quicChannel.isOpen()
                && host.equals(connectedHost) && port == connectedPort) {
            return;
        }
        closeConnection();

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
                    ch.pipeline()
                        .addLast(new GatewayFrameDecoder())
                        .addLast(new GatewayFrameEncoder())
                        .addLast(new ForyDecoder())
                        .addLast(new ForyEncoder())
                        .addLast(new ServerPushStreamHandler(clientDispatcher));
                }
            })
            .remoteAddress(new InetSocketAddress(host, port))
            .connect()
            .get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        connectedHost = host;
        connectedPort = port;
    }

    private <T> T sendOnStream(Object request, Class<T> responseType) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        ResponseStreamHandler<T> handler = new ResponseStreamHandler<>(future, responseType);

        QuicStreamChannel stream = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
                        ch.pipeline()
                            .addLast(new GatewayFrameDecoder())
                            .addLast(new GatewayFrameEncoder())
                            .addLast(new ForyDecoder())
                            .addLast(new ForyEncoder())
                            .addLast(handler);
                    }
                })
            .sync()
            .getNow();

        stream.writeAndFlush(request).addListener(writeFuture -> {
            if (!writeFuture.isSuccess()) {
                future.completeExceptionally(writeFuture.cause());
                stream.close();
                return;
            }
            stream.shutdownOutput();
        });

        try {
            return future.get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            stream.close();
            throw new IOException("Request timed out for " + request.getClass().getSimpleName(), e);
        }
    }

    private void closeConnection() {
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
        connectedHost = null;
        connectedPort = 0;
    }

    private static final class ResponseStreamHandler<T> extends SimpleChannelInboundHandler<Object> {
        private final CompletableFuture<T> future;
        private final Class<T> responseType;

        ResponseStreamHandler(CompletableFuture<T> future, Class<T> responseType) {
            this.future = future;
            this.responseType = responseType;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            try {
                future.complete(responseType.cast(msg));
                ctx.close();
            } catch (ClassCastException e) {
                future.completeExceptionally(new IOException(
                    "Unexpected response type: expected " + responseType.getSimpleName()
                        + " but received " + msg.getClass().getSimpleName(),
                    e));
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
    private static final class ServerPushStreamHandler extends SimpleChannelInboundHandler<Object> {
        private final ClientDispatcher clientDispatcher;

        ServerPushStreamHandler(ClientDispatcher clientDispatcher) {
            this.clientDispatcher = clientDispatcher;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            clientDispatcher.enqueue(msg);
            ctx.close();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }
}
