package catalyst.common.network;

/**
 * Control message explicitly sent by backend services to the Gateway
 * to signal state transitions (e.g. auth success, play success).
 */
public record GatewayControlMessage(
    String command,      // e.g. "auth_success" or "play_success"
    String sessionId,
    String worldAddress
) implements GatewayMessage {

    @Override
    public byte gatewayFlag() {
        return ServiceType.CONTROL.flag();
    }
}
