package catalyst.common.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

/**
 * Netty encoder that serializes outbound domain objects using Apache Fory
 * and wraps them in a {@link GatewayFrame} with the appropriate routing flag.
 */
@Slf4j
public final class ForyEncoder extends MessageToMessageEncoder<Object> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
        byte[] payloadBytes = ForySerializer.serialize(msg);

        byte flag = GatewayFrame.FLAG_LOBBY;
        if (msg instanceof GatewayMessage gm) {
            flag = gm.gatewayFlag();
        }

        log.debug("Encoding message: class={} flag={}", msg.getClass().getSimpleName(), flag);

        out.add(new GatewayFrame(flag, payloadBytes));
    }
}
