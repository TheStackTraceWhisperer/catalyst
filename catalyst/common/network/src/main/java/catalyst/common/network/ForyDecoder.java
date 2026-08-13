package catalyst.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * High-performance decoder that translates raw bytes into a routed Envelope.
 * Expects the ByteBuf to be a complete, unfragmented frame.
 */
public class ForyDecoder extends MessageToMessageDecoder<ByteBuf> {

    private static final Logger log = LoggerFactory.getLogger(ForyDecoder.class);

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // A valid packet must have at least the 2-byte Wire ID
        if (in.readableBytes() < 2) {
            log.error("Received malformed packet: insufficient bytes for Wire ID.");
            in.clear(); // Drop the bad data
            return;
        }

        // 1. Read the 16-bit OpCode (Java Ordinal)
        int wireId = in.readUnsignedShort();

        PacketType type;
        try {
            // 2. O(1) Contiguous array lookup to find the Enum
            type = PacketType.fromWireId(wireId);
        } catch (ArrayIndexOutOfBoundsException e) {
            log.error("Received unknown Wire ID: {}. Dropping packet.", wireId);
            in.clear();
            return;
        }

        // 3. Extract the remaining payload bytes
        int payloadLength = in.readableBytes();
        byte[] payloadBytes = new byte[payloadLength];
        in.readBytes(payloadBytes);

        try {
            // 4. Deserialize using Apache Fory
            Object payload = ForySerializer.deserialize(payloadBytes);

            // 5. Pass the typed envelope down the pipeline to the Traffic Cop
            out.add(new DecodedPacket(type, payload));

        } catch (Exception e) {
            log.error("Failed to deserialize Fory payload for PacketType: {}", type, e);
        }
    }
}