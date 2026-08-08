package catalyst.common.dto.lobby;

import catalyst.common.network.GatewayFrame;
import catalyst.common.dto.LobbyGatewayMessage;

public record PlayRequest(
    String authToken,
    long characterId
) implements LobbyGatewayMessage {
}
