package catalyst.common.dto.login;

import catalyst.common.network.GatewayMessage;
import catalyst.common.network.ServiceType;

public interface LoginGatewayMessage extends GatewayMessage {
    @Override
    default byte gatewayFlag() {
        return ServiceType.LOGIN.flag();
    }
}
