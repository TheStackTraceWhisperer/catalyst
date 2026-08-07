package catalyst.common.network;

/**
 * Single-byte routing opcode prepended to every Fury-serialized {@link MessageFrame}.
 *
 * <p>Wire format (per message):
 * <pre>
 *   [1 byte  – Opcode.code]
 *   [4 bytes – big-endian payload length]
 *   [N bytes – Fury-serialized MessageFrame]
 * </pre>
 *
 * The gateway reads only the first byte to decide which backend to route the
 * frame to, then forwards the complete buffer (opcode + length + payload) to
 * the chosen backend without full deserialization.
 */
public enum Opcode {

    // ── Client → Gateway opcodes ────────────────────────────────────────────
    /** AUTH login request */
    LOGIN       ((byte) 0x01),
    /** Lobby: list characters */
    CHAR_LIST   ((byte) 0x02),
    /** Lobby: create character */
    CHAR_CREATE ((byte) 0x03),
    /** Lobby: select character */
    CHAR_SELECT ((byte) 0x04),
    /** Lobby: delete character */
    CHAR_DELETE ((byte) 0x05),
    /** Lobby/World: begin play session */
    PLAY        ((byte) 0x06),
    /** World: keepalive ping */
    PING        ((byte) 0x07),
    /** World: end session */
    LOGOUT      ((byte) 0x08),

    // ── Gateway → Client response opcodes ───────────────────────────────────
    /** Generic response (used for both successful and error replies) */
    RESPONSE    ((byte) 0x40),

    // ── Internal / error ────────────────────────────────────────────────────
    /** Unknown / unroutable message */
    UNKNOWN     ((byte) 0xFF);

    /** The single-byte wire value. */
    public final byte code;

    Opcode(byte code) {
        this.code = code;
    }

    /**
     * Returns the {@link Opcode} whose {@link #code} matches {@code b},
     * or {@link #UNKNOWN} if no match is found.
     */
    public static Opcode fromByte(byte b) {
        for (Opcode op : values()) {
            if (op.code == b) return op;
        }
        return UNKNOWN;
    }

    /**
     * Derives the expected {@link Opcode} from a {@link MessageFrame}'s type string.
     * Used when encoding an outbound frame.
     */
    public static Opcode fromFrameType(String type) {
        if (type == null) return RESPONSE;
        return switch (type) {
            case "LOGIN"       -> LOGIN;
            case "CHAR_LIST"   -> CHAR_LIST;
            case "CHAR_CREATE" -> CHAR_CREATE;
            case "CHAR_SELECT" -> CHAR_SELECT;
            case "CHAR_DELETE" -> CHAR_DELETE;
            case "PLAY"        -> PLAY;
            case "PING"        -> PING;
            case "LOGOUT"      -> LOGOUT;
            default            -> RESPONSE; // all server responses
        };
    }
}
