package catalyst.common.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import lombok.extern.slf4j.Slf4j;
import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.config.Language;
import java.util.List;

/**
 * Netty encoder that serializes outbound domain objects using Apache Fory
 * and wraps them in a {@link GatewayFrame} with appropriate routing flags/metadata.
 */
@Slf4j
public final class ForyEncoder extends MessageToMessageEncoder<Object> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
        byte[] payloadBytes = ForySerializer.serialize(msg);
        
        byte flag = GatewayFrame.FLAG_LOBBY;
        String metadata = "";
        
        if (msg instanceof GatewayMessage gm) {
            flag = gm.gatewayFlag();
            metadata = gm.gatewayMetadata();
        }

        log.debug("Encoding message: class={} flag={} metadata={}", 
            msg.getClass().getSimpleName(), flag, metadata);

        out.add(new GatewayFrame(flag, metadata, payloadBytes));
    }
}
