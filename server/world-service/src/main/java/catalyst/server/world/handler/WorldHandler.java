package catalyst.server.world.handler;

import catalyst.common.network.MessageFrame;
import catalyst.common.network.ResponseCode;
import catalyst.common.dto.*;
import catalyst.server.world.config.ServerProperties;
import catalyst.server.world.repository.CharacterRepository;
import catalyst.server.world.repository.SessionRepository;
import catalyst.server.common.repository.AuthTicketStore;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class WorldHandler {

    private final CharacterRepository characters;
    private final SessionRepository sessions;
    private final AuthTicketStore tickets;
    private final ServerProperties props;

    public MessageFrame handlePlay(MessageFrame reqFrame) {
        PlayRequest req;
        try {
            req = ProtocolMapper.toPlayRequest(reqFrame);
        } catch (IllegalArgumentException e) {
            PlayResponse resp = PlayResponse.builder()
                .code(ResponseCode.CONFLICT)
                .message(e.getMessage())
                .build();
            return ProtocolMapper.fromPlayResponse(resp);
        }

        Long accountId = tickets.validate(req.getAuthToken());
        if (accountId == null) {
            PlayResponse resp = PlayResponse.builder()
                .code(ResponseCode.UNAUTHORIZED)
                .message("Invalid or expired auth token")
                .build();
            return ProtocolMapper.fromPlayResponse(resp);
        }
        long charId = req.getCharacterId();
        if (charId <= 0) {
            PlayResponse resp = PlayResponse.builder()
                .code(ResponseCode.CONFLICT)
                .message("characterId required")
                .build();
            return ProtocolMapper.fromPlayResponse(resp);
        }
        try {
            var identity = characters.findActiveByIdAndAccount(charId, accountId);
            if (identity.isEmpty()) {
                PlayResponse resp = PlayResponse.builder()
                    .code(ResponseCode.NOT_FOUND)
                    .message("Character not found")
                    .build();
                return ProtocolMapper.fromPlayResponse(resp);
            }
            var id = identity.get();
            String sessionId;
            try {
                sessionId = sessions.create(accountId, charId, id.currentZoneId());
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    log.info("PLAY_ERR account={} charId={} reason=already_online", accountId, charId);
                    PlayResponse resp = PlayResponse.builder()
                        .code(ResponseCode.CONFLICT)
                        .message("Account or character is already online")
                        .build();
                    return ProtocolMapper.fromPlayResponse(resp);
                }
                throw e;
            }
            int pop = sessions.getZonePopulation(id.currentZoneId());
            log.info("PLAY_OK account={} charId={} session={} zone={} pop={}", accountId, charId, sessionId, id.currentZoneId(), pop);
            
            PlayResponse resp = PlayResponse.builder()
                .code(ResponseCode.OK)
                .sessionId(sessionId)
                .accountId(accountId)
                .characterId(charId)
                .characterName(id.name())
                .zoneId(id.currentZoneId())
                .playersInZone(pop)
                .keepaliveIntervalMs(props.getKeepaliveIntervalMs())
                .homeZoneId(id.homeZoneId())
                .x(id.currentX())
                .y(id.currentY())
                .z(id.currentZ())
                .rot(id.currentHeading())
                .build();
            return ProtocolMapper.fromPlayResponse(resp);
        } catch (SQLException e) {
            log.error("PLAY_ERR account={} charId={}", accountId, charId, e);
            PlayResponse resp = PlayResponse.builder()
                .code(ResponseCode.ERROR)
                .message("Failed to start session")
                .build();
            return ProtocolMapper.fromPlayResponse(resp);
        }
    }

    public MessageFrame handlePing(MessageFrame reqFrame) {
        PingRequest req;
        try {
            req = ProtocolMapper.toPingRequest(reqFrame);
        } catch (IllegalArgumentException e) {
            PingResponse resp = PingResponse.builder()
                .code(ResponseCode.CONFLICT)
                .message(e.getMessage())
                .build();
            return ProtocolMapper.fromPingResponse(resp);
        }

        String sessionId = normalize(req.getSessionId());
        if (sessionId.isBlank()) {
            PingResponse resp = PingResponse.builder()
                .code(ResponseCode.CONFLICT)
                .message("Missing sessionId")
                .build();
            return ProtocolMapper.fromPingResponse(resp);
        }
        try {
            if (!sessions.ping(sessionId)) {
                PingResponse resp = PingResponse.builder()
                    .code(ResponseCode.NOT_FOUND)
                    .message("Session not found")
                    .build();
                return ProtocolMapper.fromPingResponse(resp);
            }
            
            PingResponse resp = PingResponse.builder()
                .type("PONG")
                .sessionId(sessionId)
                .code(ResponseCode.OK)
                .build();
            return ProtocolMapper.fromPingResponse(resp);
        } catch (SQLException e) {
            log.error("PING_ERR session={}", sessionId, e);
            PingResponse resp = PingResponse.builder()
                .code(ResponseCode.ERROR)
                .message("Failed to update keepalive")
                .build();
            return ProtocolMapper.fromPingResponse(resp);
        }
    }

    public MessageFrame handleLogout(MessageFrame reqFrame) {
        LogoutRequest req;
        try {
            req = ProtocolMapper.toLogoutRequest(reqFrame);
        } catch (IllegalArgumentException e) {
            LogoutResponse resp = LogoutResponse.builder()
                .sessionId("-")
                .code(ResponseCode.CONFLICT)
                .build();
            return ProtocolMapper.fromLogoutResponse(resp);
        }

        String sessionId = normalize(req.getSessionId());
        if (sessionId.isBlank()) {
            LogoutResponse resp = LogoutResponse.builder()
                .sessionId("-")
                .code(ResponseCode.CONFLICT)
                .build();
            return ProtocolMapper.fromLogoutResponse(resp);
        }
        try {
            sessions.delete(sessionId);
            log.info("LOGOUT session={}", sessionId);
            
            LogoutResponse resp = LogoutResponse.builder()
                .sessionId(sessionId)
                .code(ResponseCode.OK)
                .build();
            return ProtocolMapper.fromLogoutResponse(resp);
        } catch (SQLException e) {
            log.error("LOGOUT_ERR session={}", sessionId, e);
            LogoutResponse resp = LogoutResponse.builder()
                .code(ResponseCode.ERROR)
                .message("Failed to close session")
                .build();
            return ProtocolMapper.fromLogoutResponse(resp);
        }
    }

    private String normalize(String v) { return v == null ? "" : v.trim(); }
    private MessageFrame error(String code, String message) {
        return MessageFrame.builder("ERROR").put("code", code).put("message", message).build();
    }
}
