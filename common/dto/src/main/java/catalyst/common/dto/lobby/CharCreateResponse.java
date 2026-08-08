package catalyst.common.dto.lobby;

import catalyst.common.network.GatewayFrame;
import catalyst.common.network.LobbyGatewayMessage;
import catalyst.common.network.ResponseCode;

public record CharCreateResponse(
    ResponseCode code,
    String message,
    long characterId,
    String name
) implements LobbyGatewayMessage {
}
