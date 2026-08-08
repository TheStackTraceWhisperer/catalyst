package catalyst.common.dto.lobby;

import catalyst.common.network.GatewayFrame;
import catalyst.common.network.LobbyGatewayMessage;
import catalyst.common.network.ResponseCode;

public record CharDeleteResponse(
    ResponseCode code,
    String message,
    long characterId
) implements LobbyGatewayMessage {
}
