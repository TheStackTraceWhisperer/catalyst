package catalyst.server.common.network;

/**
 * Strategy interface for processing inbound packets.
 * Implementations process specific request types and return a response.
 *
 * @param <T> the request packet type
 */
public interface PacketHandler<T> {

    /** Returns the class type of the packet this handler processes. */
    Class<T> getPacketType();

    /**
     * Processes the incoming request packet and returns a response.
     * 
     * @param packet the request packet DTO
     * @param sessionId the verified session ID injected by the gateway (null if not applicable)
     */
    Object handle(T packet, String sessionId) throws Exception;
}
