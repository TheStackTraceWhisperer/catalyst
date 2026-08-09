package catalyst.common.network;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("catalyst.tls")
public class TlsProperties {
    private String certPath;
    private String keyPath;
    private String caPath;

    public String getCertPath() {
        return certPath;
    }

    public void setCertPath(String certPath) {
        this.certPath = certPath;
    }

    public String getKeyPath() {
        return keyPath;
    }

    public void setKeyPath(String keyPath) {
        this.keyPath = keyPath;
    }

    public String getCaPath() {
        return caPath;
    }

    public void setCaPath(String caPath) {
        this.caPath = caPath;
    }
}
