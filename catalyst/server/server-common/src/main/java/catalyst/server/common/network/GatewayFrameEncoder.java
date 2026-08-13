package catalyst.server.common.network;

import catalyst.common.network.ForySerializer;
import catalyst.common.network.PacketType;
import catalyst.common.network.ServiceType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encodes GatewayMessage, GatewayFrame, and raw response DTOs into bytes for internal cluster transit.
 * Pairs symmetrically with GatewayFrameDecoder.
 * Protocol: [SessionId(8)] [HasAccountId(1)] [AccountId(8)?] [HasCharId(1)] [CharId(8)?] [OpCode(2)] [ForyPayload(N)]
 */
public class GatewayFrameEncoder extends MessageToByteEncoder<Object> {

    private static final Logger log = LoggerFactory.getLogger(GatewayFrameEncoder.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) {
        try {
            ClientSession session = ctx.channel().attr(NetworkAttributes.SESSION_KEY).get();
            if (session == null) {
                session = new ClientSession(0L, null, null);
            }

            if (msg instanceof GatewayMessage gatewayMsg) {
                encodeGatewayMessage(gatewayMsg, out);
            } else if (msg instanceof GatewayFrame frame) {
                encodeGatewayFrame(session, frame, out);
            } else {
                PacketType packetType = getPacketType(msg);
                if (packetType != null) {
                    GatewayMessage wrapped = new GatewayMessage(session, new catalyst.common.network.DecodedPacket(packetType, msg));
                    encodeGatewayMessage(wrapped, out);
                } else {
                    log.warn("GatewayFrameEncoder received unknown message type: {}", msg.getClass().getName());
                }
            }
        } catch (Exception e) {
            log.error("Failed to encode message in GatewayFrameEncoder", e);
        }
    }

    private void encodeGatewayMessage(GatewayMessage msg, ByteBuf out) throws Exception {
        ClientSession session = msg.session();

        // 1. Write the permanent Gateway Session ID
        out.writeLong(session.gatewaySessionId());

        // 2. Write Account ID presence flag and value (if authenticated)
        boolean hasAccountId = session.accountId() != null;
        out.writeBoolean(hasAccountId);
        if (hasAccountId) {
            out.writeLong(session.accountId());
        }

        // 3. Write Character ID presence flag and value (if in-game)
        boolean hasCharacterId = session.characterId() != null;
        out.writeBoolean(hasCharacterId);
        if (hasCharacterId) {
            out.writeLong(session.characterId());
        }

        // 4. Write the 16-bit Wire ID (OpCode) using the enum ordinal
        out.writeShort(msg.packet().type().ordinal());

        // 5. Serialize and write the pure DTO payload using Fory
        byte[] payloadBytes = ForySerializer.serialize(msg.packet().payload());
        out.writeBytes(payloadBytes);
    }

    private void encodeGatewayFrame(ClientSession session, GatewayFrame frame, ByteBuf out) throws Exception {
        // 1. Write the permanent Gateway Session ID
        long sessionIdVal = 0L;
        if (frame.sessionId() != null && !frame.sessionId().isEmpty()) {
            try {
                sessionIdVal = Long.parseLong(frame.sessionId());
            } catch (NumberFormatException e) {
                sessionIdVal = session.gatewaySessionId();
            }
        } else {
            sessionIdVal = session.gatewaySessionId();
        }
        out.writeLong(sessionIdVal);

        // 2. Write Account ID presence flag and value (if authenticated)
        boolean hasAccountId = session.accountId() != null;
        out.writeBoolean(hasAccountId);
        if (hasAccountId) {
            out.writeLong(session.accountId());
        }

        // 3. Write Character ID presence flag and value (if in-game)
        boolean hasCharacterId = session.characterId() != null;
        out.writeBoolean(hasCharacterId);
        if (hasCharacterId) {
            out.writeLong(session.characterId());
        }

        // 4. Write the 16-bit Wire ID (OpCode)
        // If it's a CONTROL frame, use PacketType.PING_RESPONSE ordinal (or similar CONTROL type)
        int opCode = PacketType.PING_RESPONSE.ordinal();
        if (frame.flag() != ServiceType.CONTROL) {
            // Default or fallback lookup if needed
            opCode = PacketType.PING_RESPONSE.ordinal();
        }
        out.writeShort(opCode);

        // 5. Write raw payload bytes directly
        out.writeBytes(frame.payload());
    }

    private static PacketType getPacketType(Object dto) {
        String name = dto.getClass().getSimpleName();
        return switch (name) {
            case "LoginResponse" -> PacketType.LOGIN_RESPONSE;
            case "LogoutResponse" -> PacketType.LOGOUT_RESPONSE;
            case "CharListResponse" -> PacketType.CHAR_LIST_RESPONSE;
            case "CharCreateResponse" -> PacketType.CHAR_CREATE_RESPONSE;
            case "CharDeleteResponse" -> PacketType.CHAR_DELETE_RESPONSE;
            case "CharSelectResponse" -> PacketType.CHAR_SELECT_RESPONSE;
            case "PlayResponse" -> PacketType.PLAY_RESPONSE;
            case "PingResponse" -> PacketType.PING_RESPONSE;
            default -> null;
        };
    }
}