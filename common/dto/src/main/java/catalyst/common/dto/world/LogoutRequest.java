package catalyst.common.dto.world;

import catalyst.common.network.GatewayFrame;
import catalyst.common.network.WorldGatewayMessage;

public record LogoutRequest() implements WorldGatewayMessage {
}
