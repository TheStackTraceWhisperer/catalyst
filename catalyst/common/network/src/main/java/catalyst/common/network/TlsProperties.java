package catalyst.common.network;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("catalyst.tls")
public interface TlsProperties {
    /**
     * Path to the service's own TLS certificate file (PEM).
     * Mounted from the Kubernetes Secret at /certs/tls.crt
     */
    String getCertPath();

    /**
     * Path to the service's own TLS private key file (PEM).
     * Mounted from the Kubernetes Secret at /certs/tls.key
     */
    String getKeyPath();

    /**
     * Path to the CA certificate bundle used to verify peers.
     * Mounted from the Kubernetes Secret at /certs/ca.crt
     */
    String getCaPath();
}
