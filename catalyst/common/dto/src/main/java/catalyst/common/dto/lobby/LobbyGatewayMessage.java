package catalyst.common.dto.lobby;

import catalyst.common.network.GatewayMessage;
import catalyst.common.network.ServiceType;

public interface LobbyGatewayMessage extends GatewayMessage {
    @Override
    default byte gatewayFlag() {
        return ServiceType.LOBBY.flag();
    }
}
