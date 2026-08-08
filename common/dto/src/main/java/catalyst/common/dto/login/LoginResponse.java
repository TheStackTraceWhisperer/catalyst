package catalyst.common.dto.login;

import catalyst.common.network.GatewayFrame;
import catalyst.common.dto.login.LoginGatewayMessage;
import catalyst.common.network.ResponseCode;

public record LoginResponse(
    ResponseCode code,
    String message,
    String authToken,
    long accountId
) implements LoginGatewayMessage {
}
