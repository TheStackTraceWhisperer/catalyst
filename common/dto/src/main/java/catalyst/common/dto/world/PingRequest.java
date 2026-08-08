package catalyst.common.dto.world;

import catalyst.common.network.GatewayFrame;
import catalyst.common.network.GatewayMessage;

public record PingRequest() implements GatewayMessage {

    @Override
    public byte gatewayFlag() {
        return GatewayFrame.FLAG_WORLD;
    }
}
