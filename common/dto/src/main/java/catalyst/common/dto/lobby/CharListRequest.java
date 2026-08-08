package catalyst.common.dto.lobby;

import catalyst.common.network.GatewayFrame;
import catalyst.common.network.LobbyGatewayMessage;

public record CharListRequest(
    String authToken
) implements LobbyGatewayMessage {
}
