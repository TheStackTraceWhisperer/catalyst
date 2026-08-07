package catalyst.gateway.properties;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("catalyst.gateway")
public interface GatewayProperties {
    int getPort();

    String getLoginhost();
    int getLoginport();

    String getLobbyhost();
    int getLobbyport();

    String getWorldhost();
    int getWorldport();
}
