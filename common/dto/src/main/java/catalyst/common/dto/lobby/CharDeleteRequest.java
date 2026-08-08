package catalyst.common.dto.lobby;

import catalyst.common.network.GatewayFrame;
import catalyst.common.network.LobbyGatewayMessage;

public record CharDeleteRequest(
    String authToken,
    long characterId
) implements LobbyGatewayMessage {
}
