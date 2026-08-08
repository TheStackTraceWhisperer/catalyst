package catalyst.common.network;

import java.nio.charset.StandardCharsets;

/**
 * Envelope for all network payloads. Prefixes the binary payload with a routing flag and session ID.
 * Allows the Gateway to route packets without deserializing the underlying application objects.
 */
public record GatewayFrame(
  // TODO: Samuel - can we use the enum here?
    byte flag,
    // TODO: Samuel - Is sessionId really a string? It is a 16-byte UUID, but we are sending it as a UTF-8 string. We could send it as a 16-byte array instead. then remove the explicit getter
    String sessionId,
    byte[] payload
) {
    // Flag definitions
    public static final byte FLAG_CONTROL = (byte) 0x80;

    public byte[] getSessionIdBytes() {
        if (sessionId == null || sessionId.isEmpty()) {
            return new byte[0];
        }
        return sessionId.getBytes(StandardCharsets.UTF_8);
    }
}
