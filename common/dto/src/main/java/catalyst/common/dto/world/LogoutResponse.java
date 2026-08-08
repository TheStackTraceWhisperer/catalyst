package catalyst.common.dto.world;

import catalyst.common.network.GatewayFrame;
import catalyst.common.dto.world.WorldGatewayMessage;
import catalyst.common.network.ResponseCode;

public record LogoutResponse(
    String sessionId,
    ResponseCode code,
    String message
) implements WorldGatewayMessage {
}
