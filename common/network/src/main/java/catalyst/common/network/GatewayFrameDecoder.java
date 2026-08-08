package catalyst.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;

public final class GatewayFrameDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // Minimum readable bytes: 1 flag + 4 payload len = 5 bytes
        if (in.readableBytes() < 5) {
            return;
        }

        in.markReaderIndex();

        // 1. Read message type flag
        byte flag = in.readByte();

        // 2. Read payload length
        int payloadLen = in.readInt();
        if (payloadLen < 0 || payloadLen > 10_000_000) {
            throw new IllegalArgumentException("Invalid payload length: " + payloadLen);
        }

        // Check if we have enough bytes for the full payload
        if (in.readableBytes() < payloadLen) {
            in.resetReaderIndex();
            return;
        }

        // 3. Read payload bytes
        byte[] payload = new byte[payloadLen];
        in.readBytes(payload);

        out.add(new GatewayFrame(flag, payload));
    }
}
