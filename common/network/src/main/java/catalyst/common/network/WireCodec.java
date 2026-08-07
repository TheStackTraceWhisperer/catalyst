package catalyst.common.network;

import org.apache.fury.Fury;
import org.apache.fury.ThreadSafeFury;
import org.apache.fury.config.Language;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;

/**
 * Binary wire codec backed by Apache Fury for {@link MessageFrame} serialization.
 *
 * <h2>Wire format (per message)</h2>
 * <pre>
 *   ┌──────────┬──────────────────┬──────────────────────────────┐
 *   │  1 byte  │     4 bytes      │           N bytes            │
 *   │  Opcode  │  payload length  │  Fury-serialized MessageFrame│
 *   │  (byte)  │   (big-endian)   │                              │
 *   └──────────┴──────────────────┴──────────────────────────────┘
 * </pre>
 *
 * <p>The gateway needs only the first byte to determine the destination backend
 * and can forward the entire buffer verbatim without full deserialization.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Encode
 * byte[] wire = WireCodec.encode(frame);
 *
 * // Decode from a raw buffer (server-side / client-side)
 * MessageFrame frame = WireCodec.decode(wire);
 *
 * // Gateway: peek opcode without deserializing the payload
 * Opcode op = WireCodec.peekOpcode(wire);
 * }</pre>
 */
public final class WireCodec {

    // ── Fury setup ───────────────────────────────────────────────────────────

    private static final ThreadSafeFury FURY = Fury.builder()
        .withLanguage(Language.JAVA)
        // Full class registration is required for safe deserialization;
        // Fury will refuse to deserialize any class that is not listed here.
        .requireClassRegistration(true)
        .buildThreadSafeFury();

    static {
        FURY.register(MessageFrame.class);
        // LinkedHashMap is used internally by MessageFrame.Builder
        FURY.register(LinkedHashMap.class);
        // Register the immutable map impl produced by Map.copyOf()
        try {
            FURY.register(Class.forName("java.util.ImmutableCollections$MapN"));
            FURY.register(Class.forName("java.util.ImmutableCollections$Map1"));
        } catch (ClassNotFoundException ignored) {
            // These internal classes may not exist in all JVM versions; fall back gracefully.
        }
    }

    // ── Wire constants ───────────────────────────────────────────────────────

    /** Byte offset of the opcode in a raw wire buffer. */
    public static final int OPCODE_OFFSET = 0;

    /** Byte offset of the 4-byte big-endian payload length field. */
    public static final int LENGTH_OFFSET = 1;

    /** Byte offset where the Fury payload begins. */
    public static final int PAYLOAD_OFFSET = 5;

    // ── Constructor ──────────────────────────────────────────────────────────

    private WireCodec() {}

    // ── Encoding ─────────────────────────────────────────────────────────────

    /**
     * Encodes a {@link MessageFrame} into the binary wire format.
     *
     * @param frame the frame to encode
     * @return byte array containing {@code [opcode][length][fury-payload]}
     */
    public static byte[] encode(MessageFrame frame) {
        byte[] payload = FURY.serialize(frame);
        Opcode opcode = Opcode.fromFrameType(frame.type());

        ByteArrayOutputStream baos = new ByteArrayOutputStream(PAYLOAD_OFFSET + payload.length);
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeByte(opcode.code);
            dos.writeInt(payload.length);
            dos.write(payload);
        } catch (IOException e) {
            // ByteArrayOutputStream never throws IOException
            throw new IllegalStateException("Unexpected I/O error during encoding", e);
        }
        return baos.toByteArray();
    }

    /**
     * Encodes a {@link MessageFrame} and writes it directly to the given
     * {@link OutputStream}. Useful when streaming into a Netty {@code ByteBuf}
     * backed output stream.
     *
     * @param frame  the frame to encode
     * @param out    destination output stream
     */
    public static void encode(MessageFrame frame, OutputStream out) throws IOException {
        byte[] wire = encode(frame);
        out.write(wire);
    }

    // ── Decoding ─────────────────────────────────────────────────────────────

    /**
     * Decodes a {@link MessageFrame} from a complete wire byte array.
     *
     * @param wire the raw bytes (opcode + length + payload)
     * @return the deserialized {@link MessageFrame}
     * @throws IllegalArgumentException if the buffer is too short or malformed
     */
    public static MessageFrame decode(byte[] wire) {
        if (wire == null || wire.length < PAYLOAD_OFFSET) {
            throw new IllegalArgumentException(
                "Wire buffer too short (got " + (wire == null ? 0 : wire.length) +
                " bytes, minimum is " + PAYLOAD_OFFSET + ")");
        }
        int payloadLen = ((wire[1] & 0xFF) << 24)
                       | ((wire[2] & 0xFF) << 16)
                       | ((wire[3] & 0xFF) <<  8)
                       |  (wire[4] & 0xFF);
        if (wire.length < PAYLOAD_OFFSET + payloadLen) {
            throw new IllegalArgumentException(
                "Wire buffer truncated: expected " + (PAYLOAD_OFFSET + payloadLen) +
                " bytes but got " + wire.length);
        }
        // Extract the Fury payload from the wire buffer
        byte[] payload = new byte[payloadLen];
        System.arraycopy(wire, PAYLOAD_OFFSET, payload, 0, payloadLen);
        return (MessageFrame) FURY.deserialize(payload);
    }

    /**
     * Reads exactly one framed message from the given {@link InputStream}.
     * Blocks until all bytes are available.
     *
     * @param in source input stream
     * @return the deserialized {@link MessageFrame}
     */
    public static MessageFrame decode(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        dis.readByte(); // consume opcode byte (already used for routing upstream)
        int payloadLen = dis.readInt();
        byte[] payload = dis.readNBytes(payloadLen);
        return (MessageFrame) FURY.deserialize(payload);
    }

    // ── Opcode peek ──────────────────────────────────────────────────────────

    /**
     * Returns the {@link Opcode} encoded in the first byte of {@code wire}
     * without deserializing the full payload.
     *
     * @param wire raw wire bytes (must have at least 1 byte)
     * @return the resolved {@link Opcode}
     */
    public static Opcode peekOpcode(byte[] wire) {
        if (wire == null || wire.length == 0) return Opcode.UNKNOWN;
        return Opcode.fromByte(wire[0]);
    }

    /**
     * Returns the {@link Opcode} from the first byte of the buffer starting
     * at {@code offset}.
     *
     * @param buf    raw byte array
     * @param offset position of the opcode byte
     * @return the resolved {@link Opcode}
     */
    public static Opcode peekOpcode(byte[] buf, int offset) {
        if (buf == null || buf.length <= offset) return Opcode.UNKNOWN;
        return Opcode.fromByte(buf[offset]);
    }

    /**
     * Returns the total framed message size (header + payload) without
     * fully decoding the message. Useful for buffer slicing in Netty
     * pipelines.
     *
     * @param wire raw wire bytes (must have at least {@value #PAYLOAD_OFFSET} bytes)
     * @return total expected byte count for this message
     */
    public static int framedLength(byte[] wire) {
        if (wire == null || wire.length < PAYLOAD_OFFSET) {
            throw new IllegalArgumentException("Buffer too short to read length field");
        }
        int payloadLen = ((wire[1] & 0xFF) << 24)
                       | ((wire[2] & 0xFF) << 16)
                       | ((wire[3] & 0xFF) <<  8)
                       |  (wire[4] & 0xFF);
        return PAYLOAD_OFFSET + payloadLen;
    }
}
