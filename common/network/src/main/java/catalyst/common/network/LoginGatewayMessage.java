package catalyst.common.network;

public interface LoginGatewayMessage extends GatewayMessage {
    @Override
    default byte gatewayFlag() {
        return GatewayFrame.FLAG_LOGIN;
    }
}
