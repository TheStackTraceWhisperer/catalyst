package catalyst.gateway.transport;

import catalyst.common.network.GatewayFrameDecoder;
import catalyst.common.network.GatewayFrameEncoder;
import catalyst.common.network.ServiceType;
import catalyst.common.network.TlsProperties;
import catalyst.gateway.properties.GatewayProperties;
import catalyst.gateway.proxy.BackendClient;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContext;
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
    private final TlsProperties tlsProps;
    private final Map<ServiceType, BackendClient> clients = new ConcurrentHashMap<>();
    private final Map<BackendAddress, BackendClient> dynamicWorldClients = new ConcurrentHashMap<>();

    private EventLoopGroup group;
    private Channel bindChannel;
    private volatile boolean bound = false;

    public GatewayServer(GatewayProperties props, TlsProperties tlsProps) {
        this.props = props;
        this.tlsProps = tlsProps;
        for (Map.Entry<ServiceType, GatewayProperties.BackendConfig> entry : props.getBackends().entrySet()) {
            ServiceType type = entry.getKey();
            GatewayProperties.BackendConfig config = entry.getValue();
            log.info("Configuring backend client for '{}' connecting to {}:{}", type, config.host(), config.port());
            this.clients.put(type, new BackendClient(config.host(), config.port(), tlsProps));
        }
    }

    public boolean isBound() {
        return bound;
    }

    public void start() throws Exception {
        QuicSslContext sslContext = GatewayTlsContextFactory.gatewayServerContext(tlsProps);

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
                        .addLast(new RequestHandler(props, clients, dynamicWorldClients, tlsProps));
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
