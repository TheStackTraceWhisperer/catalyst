package catalyst.common.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.config.Language;
import java.util.List;

/**
 * Netty decoder that deserializes the payload of a {@link GatewayFrame} using Apache Fory.
 */
@Slf4j
public final class ForyDecoder extends MessageToMessageDecoder<GatewayFrame> {

    @Override
    protected void decode(ChannelHandlerContext ctx, GatewayFrame msg, List<Object> out) throws Exception {
        byte[] payloadBytes = msg.payload();
        if (payloadBytes == null || payloadBytes.length == 0) {
            log.warn("Received GatewayFrame with empty payload");
            return;
        }

        // Deserialize the payload using Apache Fory
        Object domainObject = ForySerializer.deserialize(payloadBytes);

        log.debug("Decoded message: flag={} type={}", msg.flag(), domainObject.getClass().getSimpleName());

        // Pass the decoded domain object down the pipeline
        out.add(domainObject);
    }
}
