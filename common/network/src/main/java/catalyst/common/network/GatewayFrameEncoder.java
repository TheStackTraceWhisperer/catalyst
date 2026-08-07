package catalyst.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public final class GatewayFrameEncoder extends MessageToByteEncoder<GatewayFrame> {

    @Override
    protected void encode(ChannelHandlerContext ctx, GatewayFrame msg, ByteBuf out) throws Exception {
        // 1. Write message flag
        out.writeByte(msg.flag());

        // 2. Write metadata length & bytes
        byte[] metadataBytes = msg.getMetadataBytes();
        out.writeShort(metadataBytes.length);
        if (metadataBytes.length > 0) {
            out.writeBytes(metadataBytes);
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
