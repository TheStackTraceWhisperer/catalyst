package catalyst.server.common.network;

import catalyst.common.network.ServiceType;

import java.nio.charset.StandardCharsets;

/**
 * Envelope for all network payloads. Prefixes the binary payload with a routing flag and session ID.
 * Allows the Gateway to route packets without deserializing the underlying application objects.
 */
public record GatewayFrame(
    ServiceType flag,
    String sessionId,
    byte[] payload
) {

    public byte[] getSessionIdBytes() {
        if (sessionId == null || sessionId.isEmpty()) {
            return new byte[0];
        }
        return sessionId.getBytes(StandardCharsets.UTF_8);
    }
}
