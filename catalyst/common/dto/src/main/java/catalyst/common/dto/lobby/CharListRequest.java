package catalyst.common.dto.lobby;

import catalyst.common.network.GatewayFrame;
import catalyst.common.dto.lobby.LobbyGatewayMessage;

public record CharListRequest(
    String authToken
) implements LobbyGatewayMessage {
}
