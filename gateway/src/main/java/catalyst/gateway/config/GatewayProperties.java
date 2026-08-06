package catalyst.gateway.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties("catalyst.gateway")
public class GatewayProperties {
    private int port = 35555;
    private String loginServiceHost = "localhost";
    private int loginServicePort = 35561;
    private String lobbyServiceHost = "localhost";
    private int lobbyServicePort = 35562;
    private String worldServiceHost = "localhost";
    private int worldServicePort = 35563;
}
