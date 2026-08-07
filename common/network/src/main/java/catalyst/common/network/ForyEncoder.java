package catalyst.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;
import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.config.Language;

import java.nio.charset.StandardCharsets;

/**
 * Netty encoder for outbound messages using Apache Fory serialization.
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
 * <p>The encoder:
 * <ol>
 *   <li>Accepts any raw domain object (e.g., {@code LoginResponse})</li>
 *   <li>Determines the routing key from the class name (e.g., {@code "LoginResponse"})</li>
 *   <li>Serializes the object using Apache Fory</li>
 *   <li>Writes the routing key length (4 bytes, big-endian)</li>
 *   <li>Writes the routing key bytes (UTF-8)</li>
 *   <li>Writes the payload length (4 bytes, big-endian)</li>
 *   <li>Writes the Fory payload bytes</li>
 * </ol>
 */
@Slf4j
public final class ForyEncoder extends MessageToByteEncoder<Object> {

    private static final ThreadSafeFory FORY = Fory.builder()
        .withLanguage(Language.JAVA)
        // WARNING: requireClassRegistration(true) is disabled for this insecure implementation
        // This allows arbitrary classes to be serialized - use only in trusted environments
        .requireClassRegistration(false)
        .buildThreadSafeFory();

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        // Determine routing key from the class name
        String routingKey = msg.getClass().getSimpleName();

        // Serialize the object using Apache Fory
        byte[] payloadBytes = FORY.serialize(msg);

        // Convert routing key to UTF-8 bytes
        byte[] routingKeyBytes = routingKey.getBytes(StandardCharsets.UTF_8);

        log.debug("Encoding message: routingKey={} payloadSize={}", routingKey, payloadBytes.length);

        // Write routing key length (4 bytes, big-endian)
        out.writeInt(routingKeyBytes.length);

        // Write routing key bytes
        out.writeBytes(routingKeyBytes);

        // Write payload length (4 bytes, big-endian)
        out.writeInt(payloadBytes.length);

        // Write Fory payload bytes
        out.writeBytes(payloadBytes);
    }
}
