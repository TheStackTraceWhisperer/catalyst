package catalyst.common.dto.login;

import catalyst.common.network.GatewayFrame;
import catalyst.common.network.GatewayMessage;

public interface LoginGatewayMessage extends GatewayMessage {
    @Override
    default byte gatewayFlag() {
        return GatewayFrame.FLAG_LOGIN;
    }
}
