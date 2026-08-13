package catalyst.server.lobby.network;

import catalyst.common.network.TlsProperties;
import catalyst.server.lobby.properties.ServerProperties;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
@RequiredArgsConstructor
public final class ServerTransport {
    static final String PROTOCOL = "catalyst-1";

    private final ServerProperties props;
    private final TlsProperties tlsProps;
    private final ServerChannelInitializer channelInitializer;

    private EventLoopGroup group;
    private Channel bindChannel;
    private volatile boolean bound = false;

    public boolean isBound() {
        return bound;
    }

    public void start() throws Exception {
        QuicSslContext sslContext = buildSslContext();

        group = new NioEventLoopGroup();
        ChannelHandler codec = new QuicServerCodecBuilder()
          .sslEngineProvider(q -> sslContext.newEngine(q.alloc()))
          .maxIdleTimeout(60, TimeUnit.SECONDS)
          .initialMaxData(10_000_000)
          .initialMaxStreamDataBidirectionalLocal(1_000_000)
          .initialMaxStreamDataBidirectionalRemote(1_000_000)
          .initialMaxStreamsBidirectional(256)
          .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
          .streamHandler(channelInitializer)
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

    private QuicSslContext buildSslContext() throws Exception {
        log.info("TLS: loading backend server context from {}", tlsProps.getCertPath());
        return QuicSslContextBuilder
          .forServer(new File(tlsProps.getKeyPath()), null, new File(tlsProps.getCertPath()))
          .trustManager(new File(tlsProps.getCaPath()))
          .clientAuth(ClientAuth.REQUIRE)
          .applicationProtocols(PROTOCOL)
          .build();
    }
}