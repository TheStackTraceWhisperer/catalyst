package catalyst.common.network;

public interface LobbyGatewayMessage extends GatewayMessage {
    @Override
    default byte gatewayFlag() {
        return GatewayFrame.FLAG_LOBBY;
    }
}
