package catalyst.common.dto.lobby;

import catalyst.common.network.GatewayFrame;
import catalyst.common.network.LobbyGatewayMessage;

public record CharSelectRequest(
    String authToken,
    long characterId
) implements LobbyGatewayMessage {
}
