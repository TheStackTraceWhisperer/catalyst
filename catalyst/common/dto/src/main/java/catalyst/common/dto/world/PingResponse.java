package catalyst.common.dto.world;

import catalyst.common.network.GatewayFrame;
import catalyst.common.dto.world.WorldGatewayMessage;
import catalyst.common.network.ResponseCode;

public record PingResponse(
    String type,
    String sessionId,
    ResponseCode code,
    String message
) implements WorldGatewayMessage {
}
