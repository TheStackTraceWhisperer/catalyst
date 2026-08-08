package catalyst.gateway.properties;

import io.micronaut.context.annotation.ConfigurationProperties;
import java.util.Map;
import java.util.HashMap;

@ConfigurationProperties("catalyst.gateway")
public class GatewayProperties {
    private int port;
    private Map<String, BackendConfig> backends = new HashMap<>();

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Map<String, BackendConfig> getBackends() {
        return backends;
    }

    public void setBackends(Map<String, BackendConfig> backends) {
        this.backends = backends;
    }

    public static class BackendConfig {
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

    public BackendConfig getBackendByFlag(byte flag) {
        for (BackendConfig config : backends.values()) {
            if (config.getFlag() == flag) {
                return config;
            }
        }
        return null;
    }
}
