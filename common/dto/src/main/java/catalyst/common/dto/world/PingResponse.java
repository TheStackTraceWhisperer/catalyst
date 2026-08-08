package catalyst.common.dto.world;

import catalyst.common.network.GatewayFrame;
import catalyst.common.network.GatewayMessage;
import catalyst.common.network.ResponseCode;

public record PingResponse(
    String type,
    String sessionId,
    ResponseCode code,
    String message
) implements GatewayMessage {

    @Override
    public byte gatewayFlag() {
        return GatewayFrame.FLAG_WORLD;
    }
}
