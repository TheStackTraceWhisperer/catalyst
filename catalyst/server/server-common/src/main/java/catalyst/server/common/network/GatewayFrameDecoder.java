package catalyst.server.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Intercepts internal traffic from the Gateway, strips the evolving ClientSession
 * metadata into the Channel Context, and yields the remaining ByteBuf (OpCode + Payload)
 * to be processed by downstream decoders.
 */
public class GatewayFrameDecoder extends MessageToMessageDecoder<ByteBuf> {

    private static final Logger log = LoggerFactory.getLogger(GatewayFrameDecoder.class);

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // Minimum size: 8 bytes (Session ID) + 1 byte (Account Flag) + 1 byte (Char Flag) + 2 bytes (OpCode) = 12 bytes
        if (in.readableBytes() < 12) {
            log.error("Received malformed Gateway Frame (too small). Dropping.");
            in.clear();
            return;
        }

        try {
            // 1. Extract the permanent Gateway Session ID
            long gatewaySessionId = in.readLong();

            // 2. Extract Account ID (if authenticated)
            boolean hasAccountId = in.readBoolean();
            Long accountId = hasAccountId ? in.readLong() : null;

            // 3. Extract Character ID (if in-game)
            boolean hasCharacterId = in.readBoolean();
            Long characterId = hasCharacterId ? in.readLong() : null;

            // 4. Construct the session and inject it into the Netty Channel Context
            ClientSession session = new ClientSession(gatewaySessionId, accountId, characterId);
            ctx.channel().attr(NetworkAttributes.SESSION_KEY).set(session);

            // 5. Slice the remaining bytes (OpCode + ForyPayload) and pass it downstream
            // readRetainedSlice avoids copying the underlying memory (Zero-Copy)
            ByteBuf remainingBytes = in.readRetainedSlice(in.readableBytes());
            out.add(remainingBytes);

        } catch (Exception e) {
            log.error("Error decoding GatewayFrame metadata. Dropping packet.", e);
            in.clear();
        }
    }
}