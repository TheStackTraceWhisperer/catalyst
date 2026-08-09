package catalyst.common.dto.login;

import catalyst.common.network.GatewayFrame;
import catalyst.common.dto.login.LoginGatewayMessage;

public record LoginRequest(
    String username,
    String password
) implements LoginGatewayMessage {
}
