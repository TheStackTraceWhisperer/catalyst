package catalyst.server.world.handler;

import catalyst.common.network.MessageFrame;
import catalyst.common.network.ResponseCode;
import catalyst.common.dto.*;
import catalyst.server.world.properties.ServerProperties;
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
            PlayResponse resp = new PlayResponse(ResponseCode.CONFLICT, e.getMessage(), null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
            return ProtocolMapper.fromPlayResponse(resp);
        }

        Long accountId = tickets.validate(req.authToken());
        if (accountId == null) {
            PlayResponse resp = new PlayResponse(ResponseCode.UNAUTHORIZED, "Invalid or expired auth token", null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
            return ProtocolMapper.fromPlayResponse(resp);
        }
        long charId = req.characterId();
        if (charId <= 0) {
            PlayResponse resp = new PlayResponse(ResponseCode.CONFLICT, "characterId required", null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
            return ProtocolMapper.fromPlayResponse(resp);
        }
        try {
            var identity = characters.findActiveByIdAndAccount(charId, accountId);
            if (identity.isEmpty()) {
                PlayResponse resp = new PlayResponse(ResponseCode.NOT_FOUND, "Character not found", null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
                return ProtocolMapper.fromPlayResponse(resp);
            }
            var id = identity.get();
            String sessionId;
            try {
                sessionId = sessions.create(accountId, charId, id.currentZoneId());
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    log.info("PLAY_ERR account={} charId={} reason=already_online", accountId, charId);
                    PlayResponse resp = new PlayResponse(ResponseCode.CONFLICT, "Account or character is already online", null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
                    return ProtocolMapper.fromPlayResponse(resp);
                }
                throw e;
            }
            int pop = sessions.getZonePopulation(id.currentZoneId());
            log.info("PLAY_OK account={} charId={} session={} zone={} pop={}", accountId, charId, sessionId, id.currentZoneId(), pop);

            PlayResponse resp = new PlayResponse(
                ResponseCode.OK, null, sessionId,
                accountId, charId, id.name(),
                id.currentZoneId(), pop,
                props.getKeepaliveIntervalMs(),
                id.homeZoneId(),
                id.currentX(), id.currentY(), id.currentZ(), id.currentHeading()
            );
            return ProtocolMapper.fromPlayResponse(resp);
        } catch (SQLException e) {
            log.error("PLAY_ERR account={} charId={}", accountId, charId, e);
            PlayResponse resp = new PlayResponse(ResponseCode.ERROR, "Failed to start session", null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
            return ProtocolMapper.fromPlayResponse(resp);
        }
    }

    public MessageFrame handlePing(MessageFrame reqFrame) {
        PingRequest req;
        try {
            req = ProtocolMapper.toPingRequest(reqFrame);
        } catch (IllegalArgumentException e) {
            PingResponse resp = new PingResponse(null, null, ResponseCode.CONFLICT, e.getMessage());
            return ProtocolMapper.fromPingResponse(resp);
        }

        String sessionId = normalize(req.sessionId());
        if (sessionId.isBlank()) {
            PingResponse resp = new PingResponse(null, null, ResponseCode.CONFLICT, "Missing sessionId");
            return ProtocolMapper.fromPingResponse(resp);
        }
        try {
            if (!sessions.ping(sessionId)) {
                PingResponse resp = new PingResponse(null, null, ResponseCode.NOT_FOUND, "Session not found");
                return ProtocolMapper.fromPingResponse(resp);
            }
            PingResponse resp = new PingResponse("PONG", sessionId, ResponseCode.OK, null);
            return ProtocolMapper.fromPingResponse(resp);
        } catch (SQLException e) {
            log.error("PING_ERR session={}", sessionId, e);
            PingResponse resp = new PingResponse(null, null, ResponseCode.ERROR, "Failed to update keepalive");
            return ProtocolMapper.fromPingResponse(resp);
        }
    }

    public MessageFrame handleLogout(MessageFrame reqFrame) {
        LogoutRequest req;
        try {
            req = ProtocolMapper.toLogoutRequest(reqFrame);
        } catch (IllegalArgumentException e) {
            LogoutResponse resp = new LogoutResponse("-", ResponseCode.CONFLICT, null);
            return ProtocolMapper.fromLogoutResponse(resp);
        }

        String sessionId = normalize(req.sessionId());
        if (sessionId.isBlank()) {
            LogoutResponse resp = new LogoutResponse("-", ResponseCode.CONFLICT, null);
            return ProtocolMapper.fromLogoutResponse(resp);
        }
        try {
            sessions.delete(sessionId);
            log.info("LOGOUT session={}", sessionId);
            LogoutResponse resp = new LogoutResponse(sessionId, ResponseCode.OK, null);
            return ProtocolMapper.fromLogoutResponse(resp);
        } catch (SQLException e) {
            log.error("LOGOUT_ERR session={}", sessionId, e);
            LogoutResponse resp = new LogoutResponse(null, ResponseCode.ERROR, "Failed to close session");
            return ProtocolMapper.fromLogoutResponse(resp);
        }
    }

    private String normalize(String v) { return v == null ? "" : v.trim(); }
    private MessageFrame error(String code, String message) {
        return MessageFrame.builder("ERROR").put("code", code).put("message", message).build();
    }
}
