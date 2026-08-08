package catalyst.common.dto.lobby;

import catalyst.common.network.GatewayFrame;
import catalyst.common.network.GatewayMessage;

public interface LobbyGatewayMessage extends GatewayMessage {
    @Override
    default byte gatewayFlag() {
        return GatewayFrame.FLAG_LOBBY;
    }
}
