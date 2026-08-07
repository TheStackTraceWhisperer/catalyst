package catalyst.common.dto;

import catalyst.common.network.GatewayFrame;
import catalyst.common.network.GatewayMessage;

public record CharListRequest(
    String authToken
) implements GatewayMessage {

    @Override
    public byte gatewayFlag() {
        return GatewayFrame.FLAG_LOBBY;
    }
}
