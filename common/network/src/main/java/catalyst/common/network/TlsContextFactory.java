package catalyst.common.network;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Factory for building {@link QuicSslContext} instances for all Catalyst service roles.
 * <p>
 * Falls back gracefully to self-signed / insecure contexts when cert files are not present
 * (e.g. during local development without a Kubernetes cluster).
 */
public final class TlsContextFactory {

    private static final Logger log = LoggerFactory.getLogger(TlsContextFactory.class);
    private static final String PROTOCOL = "catalyst-1";

    private TlsContextFactory() {}

    /**
     * Backend server context (login, lobby, world services).
     * Requires mTLS client authentication — the gateway must present its cert.
     * Falls back to SelfSignedCertificate if cert files are absent (local dev).
     */
    public static QuicSslContext backendServerContext(TlsProperties tls) throws Exception {
        if (certFilesPresent(tls)) {
            log.info("TLS: loading backend server context from {}", tls.getCertPath());
            return QuicSslContextBuilder
                .forServer(new File(tls.getKeyPath()), null, new File(tls.getCertPath()))
                .trustManager(new File(tls.getCaPath()))
                .clientAuth(ClientAuth.REQUIRE)
                .applicationProtocols(PROTOCOL)
                .build();
        }
        log.warn("TLS cert files not found — falling back to SelfSignedCertificate for backend server (dev mode)");
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        return QuicSslContextBuilder
            .forServer(ssc.key(), null, ssc.cert())
            .applicationProtocols(PROTOCOL)
            .build();
    }

    /**
     * Gateway server context (presented to game clients).
     * Does NOT require client authentication from game clients.
     * Falls back to SelfSignedCertificate if cert files are absent (local dev).
     */
    public static QuicSslContext gatewayServerContext(TlsProperties tls) throws Exception {
        if (certFilesPresent(tls)) {
            log.info("TLS: loading gateway server context from {}", tls.getCertPath());
            return QuicSslContextBuilder
                .forServer(new File(tls.getKeyPath()), null, new File(tls.getCertPath()))
                .applicationProtocols(PROTOCOL)
                .build();
        }
        log.warn("TLS cert files not found — falling back to SelfSignedCertificate for gateway server (dev mode)");
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        return QuicSslContextBuilder
            .forServer(ssc.key(), null, ssc.cert())
            .applicationProtocols(PROTOCOL)
            .build();
    }

    /**
     * Backend client context used by the gateway's {@code BackendClient}.
     * Presents the gateway's own cert (mTLS) and verifies backend server certs against the CA.
     * Falls back to InsecureTrustManagerFactory if cert files are absent (local dev).
     */
    public static QuicSslContext backendClientContext(TlsProperties tls) throws Exception {
        if (certFilesPresent(tls)) {
            log.info("TLS: loading backend client context from {}", tls.getCertPath());
            return QuicSslContextBuilder
                .forClient()
                .keyManager(new File(tls.getKeyPath()), null, new File(tls.getCertPath()))
                .trustManager(new File(tls.getCaPath()))
                .applicationProtocols(PROTOCOL)
                .build();
        }
        log.warn("TLS cert files not found — falling back to insecure trust for backend client (dev mode)");
        return QuicSslContextBuilder
            .forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .applicationProtocols(PROTOCOL)
            .build();
    }

    /**
     * Game client context — validates the gateway's server cert against the CA bundle.
     * No client cert is needed from the game client.
     * Falls back to InsecureTrustManagerFactory if caPath is blank or file is absent (local dev).
     */
    public static QuicSslContext clientContext(TlsProperties tls) throws Exception {
        String caPath = tls.getCaPath();
        if (caPath != null && !caPath.isBlank()) {
            File caFile = new File(caPath);
            if (caFile.exists()) {
                log.info("TLS: loading client CA trust from {}", caPath);
                return QuicSslContextBuilder
                    .forClient()
                    .trustManager(caFile)
                    .applicationProtocols(PROTOCOL)
                    .build();
            }
        }
        log.warn("TLS CA path not set or file not found — falling back to insecure trust for game client (dev mode)");
        return QuicSslContextBuilder
            .forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .applicationProtocols(PROTOCOL)
            .build();
    }

    private static boolean certFilesPresent(TlsProperties tls) {
        if (tls.getCertPath() == null || tls.getKeyPath() == null || tls.getCaPath() == null) {
            return false;
        }
        return new File(tls.getCertPath()).exists()
            && new File(tls.getKeyPath()).exists()
            && new File(tls.getCaPath()).exists();
    }
}
