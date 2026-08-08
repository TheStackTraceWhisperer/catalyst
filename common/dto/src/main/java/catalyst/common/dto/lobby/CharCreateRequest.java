package catalyst.common.dto.lobby;

import catalyst.common.network.GatewayFrame;
import catalyst.common.network.LobbyGatewayMessage;

public record CharCreateRequest(
    String authToken,
    String name,
    int race,
    int size,
    int face,
    int mainJob,
    String nation
) implements LobbyGatewayMessage {
}
