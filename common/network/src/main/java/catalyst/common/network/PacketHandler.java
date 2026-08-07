package catalyst.common.network;

/**
 * Strategy interface for processing inbound packets.
 * Implementations process specific request types and return a response.
 *
 * @param <T> the request packet type
 */
public interface PacketHandler<T> {

    /** Returns the class type of the packet this handler processes. */
    Class<T> getPacketType();

    /** Processes the incoming request packet and returns a response. */
    Object handle(T packet) throws Exception;
}
