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

    /**
     * Resolves and returns a wrapped X509TrustManager array that maps the QUIC-specific "GENERIC"
     * authType to "RSA" to bypass JDK validation issues under BoringSSL.
     */
    public static javax.net.ssl.TrustManager[] getWrappedTrustManagers(String caPath) throws Exception {
        if (caPath == null || caPath.isBlank()) {
            throw new IllegalArgumentException("TLS CA certificate path must be configured");
        }
        java.io.File caFile = new java.io.File(caPath);
        if (!caFile.exists()) {
            throw new java.io.FileNotFoundException("TLS CA certificate file not found: " + caPath);
        }
        
        java.security.KeyStore keyStore = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());
        keyStore.load(null, null);
        java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
        try (java.io.InputStream in = new java.io.FileInputStream(caFile)) {
            java.security.cert.Certificate cert = cf.generateCertificate(in);
            keyStore.setCertificateEntry("ca", cert);
        }
        
        javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory.getInstance(
            javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()
        );
        tmf.init(keyStore);
        
        javax.net.ssl.X509TrustManager delegate = null;
        for (javax.net.ssl.TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof javax.net.ssl.X509TrustManager) {
                delegate = (javax.net.ssl.X509TrustManager) tm;
                break;
            }
        }
        
        if (delegate == null) {
            return tmf.getTrustManagers();
        }
        
        final javax.net.ssl.X509TrustManager finalDelegate = delegate;
        return new javax.net.ssl.TrustManager[] {
            new javax.net.ssl.X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
                    String resolvedAuthType = "GENERIC".equalsIgnoreCase(authType) ? "RSA" : authType;
                    finalDelegate.checkClientTrusted(chain, resolvedAuthType);
                }
                
                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
                    String resolvedAuthType = "GENERIC".equalsIgnoreCase(authType) ? "RSA" : authType;
                    finalDelegate.checkServerTrusted(chain, resolvedAuthType);
                }
                
                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return finalDelegate.getAcceptedIssuers();
                }
            }
        };
    }
}
