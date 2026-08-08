package catalyst.common.network;

import java.nio.charset.StandardCharsets;

/**
 * Envelope for all network payloads. Prefixes the binary payload with a routing flag and session ID.
 * Allows the Gateway to route packets without deserializing the underlying application objects.
 */
public record GatewayFrame(
    byte flag,
    String sessionId,
    byte[] payload
) {
    // Flag definitions
    public static final byte FLAG_LOGIN   = 0x01;
    public static final byte FLAG_LOBBY   = 0x02;
    public static final byte FLAG_WORLD   = 0x03;
    public static final byte FLAG_CONTROL = (byte) 0x80;

    public byte[] getSessionIdBytes() {
        if (sessionId == null || sessionId.isEmpty()) {
            return new byte[0];
        }
        return sessionId.getBytes(StandardCharsets.UTF_8);
    }
}
