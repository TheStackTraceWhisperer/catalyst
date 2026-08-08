package catalyst.common.dto.world;

import catalyst.common.network.GatewayFrame;
import catalyst.common.dto.WorldGatewayMessage;

public record PingRequest() implements WorldGatewayMessage {
}
