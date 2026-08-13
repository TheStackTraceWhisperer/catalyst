package catalyst.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-performance encoder that translates a routed Envelope into raw bytes.
 * Expects a LengthFieldPrepender downstream in the pipeline to add the frame size.
 */
public class ForyEncoder extends MessageToByteEncoder<DecodedPacket> {

    private static final Logger log = LoggerFactory.getLogger(ForyEncoder.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, DecodedPacket msg, ByteBuf out) {
        try {
            // 1. Write the 16-bit Wire ID directly from the Enum ordinal
            out.writeShort(msg.type().ordinal());

            // 2. Serialize the Java DTO to bytes using Fory
            byte[] payloadBytes = ForySerializer.serialize(msg.payload());

            // 3. Write the payload bytes to the buffer
            out.writeBytes(payloadBytes);

        } catch (Exception e) {
            log.error("Failed to serialize and encode PacketType: {}", msg.type(), e);
        }
    }
}