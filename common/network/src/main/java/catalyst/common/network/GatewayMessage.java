package catalyst.common.network;

/**
 * Interface implemented by network DTO payloads to declare their destination routing flag,
 * eliminating class-name switches and reflection in the encoder.
 */
public interface GatewayMessage {

    /** Returns the target routing flag (e.g. 0x01). */
    byte gatewayFlag();
}
