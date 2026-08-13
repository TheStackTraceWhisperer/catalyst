package catalyst.server.common.network;

import catalyst.common.network.ForySerializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encodes a GatewayMessage into bytes for internal cluster transit.
 * Pairs symmetrically with GatewayFrameDecoder.
 * Protocol: [SessionId(8)] [HasAccountId(1)] [AccountId(8)?] [HasCharId(1)] [CharId(8)?] [OpCode(2)] [ForyPayload(N)]
 */
public class GatewayFrameEncoder extends MessageToByteEncoder<GatewayMessage> {

    private static final Logger log = LoggerFactory.getLogger(GatewayFrameEncoder.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, GatewayMessage msg, ByteBuf out) {
        try {
            ClientSession session = msg.session();

            // 1. Write the permanent Gateway Session ID
            out.writeLong(session.gatewaySessionId());

            // 2. Write Account ID presence flag and value (if authenticated)
            boolean hasAccountId = session.accountId() != null;
            out.writeBoolean(hasAccountId);
            if (hasAccountId) {
                out.writeLong(session.accountId());
            }

            // 3. Write Character ID presence flag and value (if in-game)
            boolean hasCharacterId = session.characterId() != null;
            out.writeBoolean(hasCharacterId);
            if (hasCharacterId) {
                out.writeLong(session.characterId());
            }

            // 4. Write the 16-bit Wire ID (OpCode) using the enum ordinal
            out.writeShort(msg.packet().type().ordinal());

            // 5. Serialize and write the pure DTO payload using Fory
            byte[] payloadBytes = ForySerializer.serialize(msg.packet().payload());
            out.writeBytes(payloadBytes);

        } catch (Exception e) {
            log.error("Failed to encode GatewayMessage for PacketType: {}", msg.packet().type(), e);
            // Optionally, you might want to close the context if encoding critically fails
            // ctx.close();
        }
    }
}