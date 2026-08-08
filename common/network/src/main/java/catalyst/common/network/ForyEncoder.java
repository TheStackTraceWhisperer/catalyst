package catalyst.common.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

/**
 * Netty encoder that serializes outbound {@link GatewayMessage} objects using Apache Fory
 * and wraps them in a {@link GatewayFrame} with the appropriate routing flag.
 */
@Slf4j
public final class ForyEncoder extends MessageToMessageEncoder<GatewayMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, GatewayMessage msg, List<Object> out) throws Exception {
        log.debug("Encoding message: class={} flag={}", msg.getClass().getSimpleName(), msg.gatewayFlag());
        ServiceType type = ServiceType.fromFlag(msg.gatewayFlag());
        if (type == null) {
            throw new IllegalArgumentException("Unknown gateway flag: " + msg.gatewayFlag());
        }
        out.add(new GatewayFrame(type, "", ForySerializer.serialize(msg)));
    }
}
