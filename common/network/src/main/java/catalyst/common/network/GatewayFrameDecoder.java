package catalyst.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class GatewayFrameDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // Minimum readable bytes: 1 flag + 2 sessionId len + 4 payload len = 7 bytes
        if (in.readableBytes() < 7) {
            return;
        }

        in.markReaderIndex();

        // 1. Read message type flag
        byte flag = in.readByte();

        // 2. Read sessionId length
        int sessionLen = in.readUnsignedShort();
        if (sessionLen < 0 || sessionLen > 512) {
            throw new IllegalArgumentException("Invalid sessionId length: " + sessionLen);
        }

        // Check if we have enough bytes for sessionId + payload length (4 bytes)
        if (in.readableBytes() < sessionLen + 4) {
            in.resetReaderIndex();
            return;
        }

        // Read sessionId bytes
        String sessionId = "";
        if (sessionLen > 0) {
            byte[] sessionBytes = new byte[sessionLen];
            in.readBytes(sessionBytes);
            sessionId = new String(sessionBytes, StandardCharsets.UTF_8);
        }

        // 3. Read payload length
        int payloadLen = in.readInt();
        if (payloadLen < 0 || payloadLen > 10_000_000) {
            throw new IllegalArgumentException("Invalid payload length: " + payloadLen);
        }

        // Check if we have enough bytes for the full payload
        if (in.readableBytes() < payloadLen) {
            in.resetReaderIndex();
            return;
        }

        // 4. Read payload bytes
        byte[] payload = new byte[payloadLen];
        in.readBytes(payload);

        out.add(new GatewayFrame(flag, sessionId, payload));
    }
}
