package catalyst.ffxi.client.network;

import catalyst.ffxi.common.net.MessageFrame;
import catalyst.ffxi.common.net.WireCodec;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;import io.netty.channel.nio.NioEventLoopGroup;
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
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class QuicGateway implements AutoCloseable {
    static final String PROTOCOL = "ffxi-1";
    private static final long REQUEST_TIMEOUT_MS = 5_000L;

    private EventLoopGroup group;
    private Channel udpChannel;
    private QuicChannel quicChannel;
    private String connectedHost;
    private int connectedPort;

    synchronized MessageFrame request(String host, int port, String type, Map<String, String> fields) throws IOException {
        try {
            ensureConnected(host, port);
            return sendOnStream(type, fields);
        } catch (IOException e) {
            closeConnection();
            throw e;
        } catch (Exception e) {
            closeConnection();
            throw new IOException("QUIC request failed: " + e.getMessage(), e);
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
                    // server-initiated streams not used; discard
                }
            })
            .remoteAddress(new InetSocketAddress(host, port))
            .connect()
            .get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        connectedHost = host;
        connectedPort = port;
    }

    private MessageFrame sendOnStream(String type, Map<String, String> fields) throws Exception {
        CompletableFuture<MessageFrame> future = new CompletableFuture<>();
        ResponseStreamHandler handler = new ResponseStreamHandler(future);

        QuicStreamChannel stream = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, handler)
            .sync()
            .getNow();

        String encoded = WireCodec.encode(type, fields) + "\n";
        stream.writeAndFlush(Unpooled.copiedBuffer(encoded, StandardCharsets.UTF_8))
            .addListener(f -> stream.shutdownOutput());

        try {
            return future.get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            stream.close();
            throw new IOException("Request timed out: " + type);
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

    private static final class ResponseStreamHandler extends ChannelInboundHandlerAdapter {
        private final CompletableFuture<MessageFrame> future;
        private final StringBuilder buffer = new StringBuilder();

        ResponseStreamHandler(CompletableFuture<MessageFrame> future) {
            this.future = future;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            try {
                buffer.append(buf.toString(StandardCharsets.UTF_8));
            } finally {
                buf.release();
            }
            int newlineIdx = buffer.indexOf("\n");
            if (newlineIdx >= 0 && !future.isDone()) {
                String line = buffer.substring(0, newlineIdx).trim();
                if (!line.isBlank()) {
                    try {
                        future.complete(WireCodec.decode(line));
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                } else {
                    future.completeExceptionally(new IOException("Empty response from server"));
                }
                ctx.close();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            // Stream closed before a full line arrived
            if (!future.isDone()) {
                String line = buffer.toString().trim();
                if (!line.isBlank()) {
                    try {
                        future.complete(WireCodec.decode(line));
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                } else {
                    future.completeExceptionally(new IOException("Empty response from server"));
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            future.completeExceptionally(cause);
            ctx.close();
        }
    }
}
