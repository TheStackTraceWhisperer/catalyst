package catalyst.common.network;

/**
 * The typed envelope holding a parsed Java object and its routing type.
 * This is what your Netty ChannelRead0 method will consume.
 */
public record DecodedPacket(PacketType type, Object payload) {}