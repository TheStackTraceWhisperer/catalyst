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
}