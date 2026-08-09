package catalyst.common.dto.lobby;

import catalyst.common.network.GatewayFrame;
import catalyst.common.dto.lobby.LobbyGatewayMessage;

public record PlayRequest(
    String authToken,
    long characterId
) implements LobbyGatewayMessage {
}
