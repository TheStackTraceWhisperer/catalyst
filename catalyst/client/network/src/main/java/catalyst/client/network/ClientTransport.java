package catalyst.client.network;

import catalyst.common.network.DecodedPacket;
import catalyst.common.network.ForyDecoder;
import catalyst.common.network.ForyEncoder;
import catalyst.common.network.TlsProperties;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicClientCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RequiredArgsConstructor
public final class ClientTransport implements AutoCloseable {
    static final String PROTOCOL = "catalyst-1";
    private static final long REQUEST_TIMEOUT_MS = 5_000L;

    private final ClientChannelInitializer initializer;
    private final TlsProperties tlsProps;

    private EventLoopGroup group;
    private Channel udpChannel;
    private QuicChannel quicChannel;
    private String connectedHost;
    private int connectedPort;

    /**
     * Synchronous request/response RPC stream over QUIC.
     */
    synchronized <T> T request(String host, int port, DecodedPacket requestPacket, Class<T> responseType) throws IOException {
        try {
            ensureConnected(host, port);
            return sendOnStream(requestPacket, responseType);
        } catch (IOException e) {
            closeConnection();
            throw e;
        } catch (Exception e) {
            closeConnection();
            throw new IOException("QUIC request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Asynchronous fire-and-forget message stream over QUIC.
     * Uses the injected ClientChannelInitializer so inbound server pushes on this stream
     * route directly through InboundPacketHandler.
     */
    synchronized void sendAsync(String host, int port, DecodedPacket packet) {
        try {
            ensureConnected(host, port);
            quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, initializer)
              .addListener(future -> {
                  if (future.isSuccess()) {
                      QuicStreamChannel stream = (QuicStreamChannel) future.getNow();
                      stream.writeAndFlush(packet).addListener(writeFuture -> {
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

        QuicSslContext sslContext = buildClientContext(tlsProps);

        group = new NioEventLoopGroup(1);
        String verifyHost = System.getProperty("catalyst.tls.verify-host", host);
        io.netty.channel.ChannelHandler codec = new QuicClientCodecBuilder()
          .sslEngineProvider(q -> sslContext.newEngine(q.alloc(), verifyHost, port))
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

        // Server-initiated streams automatically route through our ClientChannelInitializer
        quicChannel = QuicChannel.newBootstrap(udpChannel)
          .streamHandler(initializer)
          .remoteAddress(new InetSocketAddress(host, port))
          .connect()
          .get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        connectedHost = host;
        connectedPort = port;
    }

    private <T> T sendOnStream(DecodedPacket requestPacket, Class<T> responseType) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        ResponseStreamHandler<T> handler = new ResponseStreamHandler<>(future, responseType);

        QuicStreamChannel stream = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
            new io.netty.channel.ChannelInitializer<QuicStreamChannel>() {
                @Override
                protected void initChannel(QuicStreamChannel ch) {
                    ch.pipeline()
                      .addLast(new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2))
                      .addLast(new ForyDecoder())
                      .addLast(new LengthFieldPrepender(2))
                      .addLast(new ForyEncoder())
                      .addLast(handler);
                }
            })
          .sync()
          .getNow();

        stream.writeAndFlush(requestPacket).addListener(writeFuture -> {
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
            throw new IOException("Request timed out for " + requestPacket.payload().getClass().getSimpleName(), e);
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

    /**
     * Temporary stream handler for blocking RPC-style request/response operations.
     * Expects a DecodedPacket from ForyDecoder, extracts the payload, and completes the future.
     */
    private static final class ResponseStreamHandler<T> extends SimpleChannelInboundHandler<DecodedPacket> {
        private final CompletableFuture<T> future;
        private final Class<T> responseType;

        ResponseStreamHandler(CompletableFuture<T> future, Class<T> responseType) {
            this.future = future;
            this.responseType = responseType;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DecodedPacket msg) {
            try {
                future.complete(responseType.cast(msg.payload()));
                ctx.close();
            } catch (ClassCastException e) {
                future.completeExceptionally(new IOException(
                  "Unexpected response type: expected " + responseType.getSimpleName()
                    + " but received " + msg.payload().getClass().getSimpleName(),
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

    private QuicSslContext buildClientContext(TlsProperties tls) throws Exception {
        return QuicSslContextBuilder
          .forClient()
          .trustManager(new File(tls.getCaPath()))
          .applicationProtocols(PROTOCOL)
          .build();
    }
}