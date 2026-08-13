package catalyst.server.common.network;

/**
 * Internal out-of-band control envelope returned by backend microservices
 * to signal session state transitions inside the Gateway proxy (e.g. auth_success, play_success).
 *
 * @param command The Gateway transition command ("auth_success", "play_success", "logout_success").
 * @param worldAddress Target world server address ("host:port") populated during play_success transitions.
 * @param sessionId Assigned session identifier attached during play_success.
 */
public record GatewayControlMessage(
  String command,
  String worldAddress,
  String sessionId
) {
  /**
   * Factory constructor for authentication or logout control signals.
   */
  public GatewayControlMessage(String command) {
    this(command, null, null);
  }

  /**
   * Factory constructor for world transition control signals.
   */
  public GatewayControlMessage(String command, String worldAddress, String sessionId) {
    this.command = command;
    this.worldAddress = worldAddress;
    this.sessionId = sessionId;
  }
}