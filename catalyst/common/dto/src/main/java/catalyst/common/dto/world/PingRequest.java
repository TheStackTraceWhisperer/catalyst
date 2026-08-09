package catalyst.common.dto.world;

import catalyst.common.network.GatewayFrame;
import catalyst.common.dto.world.WorldGatewayMessage;

public record PingRequest() implements WorldGatewayMessage {
}
