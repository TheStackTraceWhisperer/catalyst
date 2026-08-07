package catalyst.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class GatewayFrameDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // Minimum readable bytes: 1 flag + 2 metadata len + 4 payload len = 7 bytes
        if (in.readableBytes() < 7) {
            return;
        }

        in.markReaderIndex();

        // 1. Read message type flag
        byte flag = in.readByte();

        // 2. Read metadata length
        int metadataLen = in.readUnsignedShort();
        if (metadataLen < 0 || metadataLen > 4096) {
            throw new IllegalArgumentException("Invalid metadata length: " + metadataLen);
        }

        // Check if we have enough bytes for metadata + payload length field (4 bytes)
        if (in.readableBytes() < metadataLen + 4) {
            in.resetReaderIndex();
            return;
        }

        // Read metadata bytes
        String metadata = "";
        if (metadataLen > 0) {
            byte[] metadataBytes = new byte[metadataLen];
            in.readBytes(metadataBytes);
            metadata = new String(metadataBytes, StandardCharsets.UTF_8);
        }

        // 3. Read payload length
        int payloadLen = in.readInt();
        if (payloadLen < 0 || payloadLen > 10_000_000) {
            throw new IllegalArgumentException("Invalid payload length: " + payloadLen);
        }

        // Check if we have enough bytes for payload
        if (in.readableBytes() < payloadLen) {
            in.resetReaderIndex();
            return;
        }

        // Read payload bytes
        byte[] payload = new byte[payloadLen];
        in.readBytes(payload);

        // Output GatewayFrame
        out.add(new GatewayFrame(flag, metadata, payload));
    }
}
