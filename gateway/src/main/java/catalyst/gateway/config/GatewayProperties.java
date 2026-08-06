package catalyst.gateway.config;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import lombok.Getter;

@Getter
@Singleton
public class GatewayProperties {
    private final int port;
    private final String loginServiceHost;
    private final int loginServicePort;
    private final String lobbyServiceHost;
    private final int lobbyServicePort;
    private final String worldServiceHost;
    private final int worldServicePort;

    public GatewayProperties(
        @Value("${CATALYST_GATEWAY_PORT:35555}") int port,
        @Value("${CATALYST_GATEWAY_LOGIN_SERVICE_HOST}") String loginServiceHost,
        @Value("${CATALYST_GATEWAY_LOGIN_SERVICE_PORT}") int loginServicePort,
        @Value("${CATALYST_GATEWAY_LOBBY_SERVICE_HOST}") String lobbyServiceHost,
        @Value("${CATALYST_GATEWAY_LOBBY_SERVICE_PORT}") int lobbyServicePort,
        @Value("${CATALYST_GATEWAY_WORLD_SERVICE_HOST}") String worldServiceHost,
        @Value("${CATALYST_GATEWAY_WORLD_SERVICE_PORT}") int worldServicePort
    ) {
        this.port = port;
        this.loginServiceHost = loginServiceHost;
        this.loginServicePort = loginServicePort;
        this.lobbyServiceHost = lobbyServiceHost;
        this.lobbyServicePort = lobbyServicePort;
        this.worldServiceHost = worldServiceHost;
        this.worldServicePort = worldServicePort;
    }
}
