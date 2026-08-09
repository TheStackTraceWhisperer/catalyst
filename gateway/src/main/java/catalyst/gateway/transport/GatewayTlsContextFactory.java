package catalyst.gateway.transport;

import catalyst.common.network.TlsProperties;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;

public final class GatewayTlsContextFactory {
    private static final Logger log = LoggerFactory.getLogger(GatewayTlsContextFactory.class);
    private static final String PROTOCOL = "catalyst-1";

    private GatewayTlsContextFactory() {}

    public static QuicSslContext gatewayServerContext(TlsProperties tls) throws Exception {
        log.info("TLS: loading gateway server context from {}", tls.getCertPath());
        return QuicSslContextBuilder
            .forServer(new File(tls.getKeyPath()), null, new File(tls.getCertPath()))
            .applicationProtocols(PROTOCOL)
            .build();
    }

    public static QuicSslContext backendClientContext(TlsProperties tls) throws Exception {
        log.info("TLS: loading backend client context from {}", tls.getCertPath());
        return QuicSslContextBuilder
            .forClient()
            .keyManager(new File(tls.getKeyPath()), null, new File(tls.getCertPath()))
            .trustManager(TlsProperties.getWrappedTrustManagers(tls.getCaPath())[0])
            .applicationProtocols(PROTOCOL)
            .build();
    }
}
