package catalyst.common.network;

/**
 * Interface implemented by network DTO payloads to declare their destination routing flag,
 * eliminating class-name switches and reflection in the encoder.
 */
public interface GatewayMessage {

    // TODO: Samuel - This should be returning the actual ServiceType enum, not just the byte flag. The encoder can then call ServiceType.flag() to get the byte values at the transport layer. This will allow the encoder to use the ServiceType enum for routing instead of just the byte value, which is more type-safe and maintainable.
    /** Returns the target routing flag (e.g. 0x01). */
    byte gatewayFlag();
}
