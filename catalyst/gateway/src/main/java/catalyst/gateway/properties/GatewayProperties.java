package catalyst.gateway.properties;

import catalyst.common.network.ServiceType;
import io.micronaut.context.annotation.ConfigurationProperties;
import java.util.Map;

@ConfigurationProperties("catalyst.gateway")
public interface GatewayProperties {
    int getPort();
    Map<ServiceType, BackendConfig> getBackends();

    record BackendConfig(
        String policy,
        String host,
        int port
    ) {}

    default BackendConfig getBackendByFlag(byte flag) {
        ServiceType type = ServiceType.fromFlag(flag);
        if (type == ServiceType.WORLD) {
            return new BackendConfig("SESSION_BOUND", "", 0);
        }
        if (type == null || getBackends() == null) {
            return null;
        }
        return getBackends().get(type);
    }
}
