package catalyst.common.network;

/**
 * Envelope for all network payloads. Prefixes the binary payload with a routing flag.
 * Allows the Gateway to route packets without deserializing the underlying application objects.
 */
public record GatewayFrame(
    byte flag,
    byte[] payload
) {
    // Flag definitions
    public static final byte FLAG_LOGIN   = 0x01;
    public static final byte FLAG_LOBBY   = 0x02;
    public static final byte FLAG_WORLD   = 0x03;
    public static final byte FLAG_CONTROL = (byte) 0x80;
}
