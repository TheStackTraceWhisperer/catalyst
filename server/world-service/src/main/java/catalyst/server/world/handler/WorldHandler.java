package catalyst.server.world.handler;

import catalyst.common.dto.LogoutRequest;
import catalyst.common.dto.LogoutResponse;
import catalyst.common.dto.PingRequest;
import catalyst.common.dto.PingResponse;
import catalyst.common.dto.PlayRequest;
import catalyst.common.dto.PlayResponse;
import catalyst.common.network.ResponseCode;
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

    public PlayResponse handlePlay(PlayRequest req) {
        if (req == null) {
            return new PlayResponse(ResponseCode.CONFLICT, "Invalid play request", null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
        }

        Long accountId = tickets.validate(req.authToken());
        if (accountId == null) {
            return new PlayResponse(ResponseCode.UNAUTHORIZED, "Invalid or expired auth token", null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
        }
        long charId = req.characterId();
        if (charId <= 0) {
            return new PlayResponse(ResponseCode.CONFLICT, "characterId required", null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
        }
        try {
            var identity = characters.findActiveByIdAndAccount(charId, accountId);
            if (identity.isEmpty()) {
                return new PlayResponse(ResponseCode.NOT_FOUND, "Character not found", null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
            }
            var id = identity.get();
            String sessionId;
            try {
                sessionId = sessions.create(accountId, charId, id.currentZoneId());
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    log.info("PLAY_ERR account={} charId={} reason=already_online", accountId, charId);
                    return new PlayResponse(ResponseCode.CONFLICT, "Account or character is already online", null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
                }
                throw e;
            }
            int pop = sessions.getZonePopulation(id.currentZoneId());
            log.info("PLAY_OK account={} charId={} session={} zone={} pop={}", accountId, charId, sessionId, id.currentZoneId(), pop);

            return new PlayResponse(
                ResponseCode.OK, null, sessionId,
                accountId, charId, id.name(),
                id.currentZoneId(), pop,
                props.getKeepaliveIntervalMs(),
                id.homeZoneId(),
                id.currentX(), id.currentY(), id.currentZ(), id.currentHeading()
            );
        } catch (SQLException e) {
            log.error("PLAY_ERR account={} charId={}", accountId, charId, e);
            return new PlayResponse(ResponseCode.ERROR, "Failed to start session", null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
        }
    }

    public PingResponse handlePing(PingRequest req) {
        if (req == null) {
            return new PingResponse(null, null, ResponseCode.CONFLICT, "Invalid ping request");
        }

        String sessionId = normalize(req.sessionId());
        if (sessionId.isBlank()) {
            return new PingResponse(null, null, ResponseCode.CONFLICT, "Missing sessionId");
        }
        try {
            if (!sessions.ping(sessionId)) {
                return new PingResponse(null, null, ResponseCode.NOT_FOUND, "Session not found");
            }
            return new PingResponse("PONG", sessionId, ResponseCode.OK, null);
        } catch (SQLException e) {
            log.error("PING_ERR session={}", sessionId, e);
            return new PingResponse(null, null, ResponseCode.ERROR, "Failed to update keepalive");
        }
    }

    public LogoutResponse handleLogout(LogoutRequest req) {
        if (req == null) {
            return new LogoutResponse("-", ResponseCode.CONFLICT, null);
        }

        String sessionId = normalize(req.sessionId());
        if (sessionId.isBlank()) {
            return new LogoutResponse("-", ResponseCode.CONFLICT, null);
        }
        try {
            sessions.delete(sessionId);
            log.info("LOGOUT session={}", sessionId);
            return new LogoutResponse(sessionId, ResponseCode.OK, null);
        } catch (SQLException e) {
            log.error("LOGOUT_ERR session={}", sessionId, e);
            return new LogoutResponse(null, ResponseCode.ERROR, "Failed to close session");
        }
    }

    private String normalize(String v) { return v == null ? "" : v.trim(); }
}
