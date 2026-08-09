package catalyst.common.dto.world;

import catalyst.common.network.GatewayMessage;
import catalyst.common.network.ServiceType;

public interface WorldGatewayMessage extends GatewayMessage {
    @Override
    default byte gatewayFlag() {
        return ServiceType.WORLD.flag();
    }
}
