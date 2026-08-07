package catalyst.client.network;

import catalyst.common.network.MessageFrame;
import catalyst.common.network.WireCodec;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
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

final class QuicGateway implements AutoCloseable {
    static final String PROTOCOL = "catalyst-1";
    private static final long REQUEST_TIMEOUT_MS = 5_000L;

    private EventLoopGroup group;
    private Channel udpChannel;
    private QuicChannel quicChannel;
    private String connectedHost;
    private int connectedPort;

    synchronized MessageFrame request(String host, int port, MessageFrame frame) throws IOException {
        try {
            ensureConnected(host, port);
            return sendOnStream(WireCodec.encode(frame));
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

    private MessageFrame sendOnStream(byte[] rawFrame) throws Exception {
        CompletableFuture<MessageFrame> future = new CompletableFuture<>();
        ResponseStreamHandler handler = new ResponseStreamHandler(future);

        QuicStreamChannel stream = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, handler)
            .sync()
            .getNow();

        stream.writeAndFlush(Unpooled.wrappedBuffer(rawFrame))
            .addListener(f -> stream.shutdownOutput());

        try {
            return future.get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            stream.close();
            throw new IOException("Request timed out: " + rawFrame[0]);
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
        /** Byte accumulation buffer for framed binary response. */
        private byte[] buffer = new byte[0];

        ResponseStreamHandler(CompletableFuture<MessageFrame> future) {
            this.future = future;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            try {
                int readable = buf.readableBytes();
                byte[] chunk = new byte[readable];
                buf.readBytes(chunk);
                buffer = concat(buffer, chunk);
            } finally {
                buf.release();
            }
            tryDecode(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (!future.isDone()) {
                tryDecode(ctx);
                if (!future.isDone()) {
                    future.completeExceptionally(new IOException("Stream closed before full response received"));
                }
            }
        }

        private void tryDecode(ChannelHandlerContext ctx) {
            if (future.isDone()) return;
            if (buffer.length < WireCodec.PAYLOAD_OFFSET) return;
            int totalLen;
            try {
                totalLen = WireCodec.framedLength(buffer);
            } catch (IllegalArgumentException e) {
                return;
            }
            if (buffer.length < totalLen) return;

            try {
                future.complete(WireCodec.decode(buffer));
                ctx.close();
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            future.completeExceptionally(cause);
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
