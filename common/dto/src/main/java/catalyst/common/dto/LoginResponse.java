package catalyst.common.dto;

import catalyst.common.network.GatewayFrame;
import catalyst.common.network.GatewayMessage;
import catalyst.common.network.ResponseCode;

public record LoginResponse(
    ResponseCode code,
    String message,
    String authToken,
    long accountId
) implements GatewayMessage {

    @Override
    public byte gatewayFlag() {
        return GatewayFrame.FLAG_LOGIN;
    }

    @Override
    public String gatewayMetadata() {
        return code == ResponseCode.OK ? "status=auth_success" : "";
    }
}
