package catalyst.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.config.Language;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Netty decoder for inbound messages using Apache Fory serialization.
 *
 * <h2>Wire format (per message)</h2>
 * <pre>
 *   ┌──────────────────┬──────────────────────────────┬──────────────────┬──────────────────────────────┐
 *   │     4 bytes      │           N bytes            │     4 bytes      │           M bytes            │
 *   │ routing key len  │  routing key (UTF-8 string)  │  payload length  │  Fory-serialized payload     │
 *   │  (big-endian)    │                              │  (big-endian)    │                              │
 *   └──────────────────┴──────────────────────────────┴──────────────────┴──────────────────────────────┘
 * </pre>
 *
 * <p>The decoder:
 * <ol>
 *   <li>Reads the 4-byte routing key length</li>
 *   <li>Reads the routing key string (UTF-8)</li>
 *   <li>Reads the 4-byte Fory payload length</li>
 *   <li>Reads the Fory payload bytes</li>
 *   <li>Deserializes the bytes using Apache Fory</li>
 *   <li>Passes the raw domain object down the pipeline (no wrapper)</li>
 * </ol>
 *
 * <p><strong>IMPORTANT:</strong> This decoder outputs raw domain objects (e.g., {@code LoginRequest},
 * {@code PingRequest}) directly to the pipeline. The routing key is attached to the message as a
 * custom attribute or can be stored in a thread-local context if needed.
 */
@Slf4j
public final class ForyDecoder extends ByteToMessageDecoder {

    private static final ThreadSafeFory FORY = Fory.builder()
        .withLanguage(Language.JAVA)
        // WARNING: requireClassRegistration(true) is disabled for this insecure implementation
        // This allows arbitrary classes to be deserialized - use only in trusted environments
        .requireClassRegistration(false)
        .buildThreadSafeFory();

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // We need at least 4 bytes to read the routing key length
        if (in.readableBytes() < 4) {
            return; // wait for more data
        }

        // Mark the reader index in case we need to reset
        in.markReaderIndex();

        // Read routing key length
        int routingKeyLen = in.readInt();
        if (routingKeyLen < 0 || routingKeyLen > 1024) {
            throw new IllegalArgumentException("Invalid routing key length: " + routingKeyLen);
        }

        // Check if we have enough bytes for the routing key
        if (in.readableBytes() < routingKeyLen) {
            in.resetReaderIndex();
            return; // wait for more data
        }

        // Read routing key string
        byte[] routingKeyBytes = new byte[routingKeyLen];
        in.readBytes(routingKeyBytes);
        String routingKey = new String(routingKeyBytes, StandardCharsets.UTF_8);

        // Check if we have at least 4 bytes for payload length
        if (in.readableBytes() < 4) {
            in.resetReaderIndex();
            return; // wait for more data
        }

        // Read Fory payload length
        int payloadLen = in.readInt();
        if (payloadLen < 0 || payloadLen > 10_000_000) {
            throw new IllegalArgumentException("Invalid payload length: " + payloadLen);
        }

        // Check if we have enough bytes for the payload
        if (in.readableBytes() < payloadLen) {
            in.resetReaderIndex();
            return; // wait for more data
        }

        // Read Fory payload bytes
        byte[] payloadBytes = new byte[payloadLen];
        in.readBytes(payloadBytes);

        // Deserialize the payload using Apache Fory
        Object domainObject = FORY.deserialize(payloadBytes);

        log.debug("Decoded message: routingKey={} type={}", routingKey, domainObject.getClass().getSimpleName());

        // Attach routing key as a channel attribute (optional, for routing decisions)
        ctx.channel().attr(RoutingContext.ROUTING_KEY).set(routingKey);

        // Pass the raw domain object down the pipeline
        out.add(domainObject);
    }
}
