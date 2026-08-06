package catalyst.gateway.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties("catalyst.gateway")
public class GatewayProperties {
    private int port = 35555;
    private String loginServiceHost;
    private int loginServicePort;
    private String lobbyServiceHost;
    private int lobbyServicePort;
    private String worldServiceHost;
    private int worldServicePort;
}
