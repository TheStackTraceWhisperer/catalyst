package catalyst.gateway.properties;

import io.micronaut.context.annotation.ConfigurationProperties;
import java.util.Map;

@ConfigurationProperties("catalyst.gateway")
public interface GatewayProperties {
    int getPort();
    Map<String, BackendConfig> getBackends();

    class BackendConfig {
        private byte flag;
        private String policy;
        private String host;
        private int port;

        public byte getFlag() {
            return flag;
        }

        public void setFlag(byte flag) {
            this.flag = flag;
        }

        public String getPolicy() {
            return policy;
        }

        public void setPolicy(String policy) {
            this.policy = policy;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    default BackendConfig getBackendByFlag(byte flag) {
        if (getBackends() == null) {
            return null;
        }
        for (BackendConfig config : getBackends().values()) {
            if (config.getFlag() == flag) {
                return config;
            }
        }
        return null;
    }
}
