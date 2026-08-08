package catalyst.gateway.transport;

import catalyst.common.network.GatewayFrameDecoder;
import catalyst.common.network.GatewayFrameEncoder;
import catalyst.gateway.properties.GatewayProperties;
import catalyst.gateway.proxy.BackendClient;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import jakarta.inject.Singleton;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public final class GatewayServer {

    private final GatewayProperties props;
    private final Map<String, BackendClient> clients = new ConcurrentHashMap<>();
    private final Map<BackendAddress, BackendClient> dynamicWorldClients = new ConcurrentHashMap<>();

    private EventLoopGroup group;
    private Channel bindChannel;
    private volatile boolean bound = false;

    public GatewayServer(GatewayProperties props) {
        this.props = props;
        for (Map.Entry<String, GatewayProperties.BackendConfig> entry : props.getBackends().entrySet()) {
            GatewayProperties.BackendConfig config = entry.getValue();
            log.info("Configuring backend client for '{}' connecting to {}:{}", entry.getKey(), config.getHost(), config.getPort());
            this.clients.put(entry.getKey(), new BackendClient(config.getHost(), config.getPort()));
        }
    }

    public boolean isBound() {
        return bound;
    }

    public void start() throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();
        QuicSslContext sslContext = QuicSslContextBuilder.forServer(cert.key(), null, cert.cert())
            .applicationProtocols(BackendClient.PROTOCOL)
            .build();

        group = new NioEventLoopGroup();
        io.netty.channel.ChannelHandler codec = new QuicServerCodecBuilder()
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
                        .addLast(new RequestHandler(props, clients, dynamicWorldClients));
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
        log.info("Gateway Server bound on UDP port {}", props.getPort());
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
        clients.values().forEach(BackendClient::close);
        clients.clear();
        dynamicWorldClients.values().forEach(BackendClient::close);
        dynamicWorldClients.clear();
    }
}
