package catalyst.common.network;

/**
 * Control message explicitly sent by backend services to the Gateway
 * to signal state transitions (e.g. auth success, play success).
 */
public record GatewayControlMessage(
  // TODO: Samuel - Consider using an enum for command instead of a string
    String command,      // e.g. "auth_success" or "play_success"
    String sessionId,
    String worldAddress
) implements GatewayMessage {

    @Override
    public byte gatewayFlag() {
        return GatewayFrame.FLAG_CONTROL;
    }
}
