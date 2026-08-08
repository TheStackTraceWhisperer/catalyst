package catalyst.common.dto.login;

import catalyst.common.network.GatewayFrame;
import catalyst.common.dto.LoginGatewayMessage;

public record LoginRequest(
    String username,
    String password
) implements LoginGatewayMessage {
}
