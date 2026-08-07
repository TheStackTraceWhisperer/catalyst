package catalyst.common.network;

/**
 * Interface implemented by network DTO payloads to define their destination flags
 * and routing metadata, eliminating class-name switches and reflection in the encoder.
 */
public interface GatewayMessage {
    
    /** Returns the target routing flag (e.g. GatewayFrame.FLAG_LOGIN). */
    byte gatewayFlag();

    /** Returns optional metadata string for gateway state transitions. */
    default String gatewayMetadata() {
        return "";
    }
}
