package catalyst.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public final class GatewayFrameEncoder extends MessageToByteEncoder<GatewayFrame> {

    @Override
    protected void encode(ChannelHandlerContext ctx, GatewayFrame msg, ByteBuf out) throws Exception {
        // 1. Write the message flag
        out.writeByte(msg.flag());

        // 2. Write sessionId length & bytes
        byte[] sessionBytes = msg.getSessionIdBytes();
        out.writeShort(sessionBytes.length);
        if (sessionBytes.length > 0) {
            out.writeBytes(sessionBytes);
        }

        // 3. Write payload length & bytes
        byte[] payload = msg.payload();
        if (payload == null) {
            out.writeInt(0);
        } else {
            out.writeInt(payload.length);
            if (payload.length > 0) {
                out.writeBytes(payload);
            }
        }
    }
}
