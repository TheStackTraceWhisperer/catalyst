package catalyst.common.network;

public interface WorldGatewayMessage extends GatewayMessage {
    @Override
    default byte gatewayFlag() {
        return GatewayFrame.FLAG_WORLD;
    }
}
