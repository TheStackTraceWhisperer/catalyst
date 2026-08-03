package catalyst.ffxi.server.handler;

import catalyst.ffxi.common.net.MessageFrame;
import catalyst.ffxi.server.repository.CharacterRepository;
import catalyst.ffxi.server.repository.SessionRepository;
import catalyst.ffxi.server.session.AuthTicketStore;
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

    public MessageFrame handlePlay(MessageFrame req) {
        Long accountId = tickets.validate(req.get("authToken"));
        if (accountId == null) return error("UNAUTHORIZED", "Invalid or expired auth token");
        long charId = req.getLong("characterId", -1);
        if (charId <= 0) return error("INVALID_CHARACTER", "characterId required");
        try {
            var identity = characters.findActiveByIdAndAccount(charId, accountId);
            if (identity.isEmpty()) return error("CHARACTER_NOT_FOUND", "Character not found");
            var id = identity.get();
            String sessionId;
            try {
                sessionId = sessions.create(accountId, charId, id.currentZoneId());
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    log.info("PLAY_ERR account={} charId={} reason=already_online", accountId, charId);
                    return error("ALREADY_ONLINE", "Account or character is already online");
                }
                throw e;
            }
            int pop = sessions.getZonePopulation(id.currentZoneId());
            log.info("PLAY_OK account={} charId={} session={} zone={} pop={}", accountId, charId, sessionId, id.currentZoneId(), pop);
            return MessageFrame.builder("PLAY_OK")
                .put("sessionId",     sessionId)
                .put("accountId",     accountId)
                .put("characterId",   charId)
                .put("characterName", id.name())
                .put("zoneId",        id.currentZoneId())
                .put("playersInZone", pop)
                .put("homeZoneId",    id.homeZoneId())
                .put("x",   id.currentX())
                .put("y",   id.currentY())
                .put("z",   id.currentZ())
                .put("rot", id.currentHeading())
                .build();
        } catch (SQLException e) {
            log.error("PLAY_ERR account={} charId={}", accountId, charId, e);
            return error("SERVER_ERROR", "Failed to start session");
        }
    }

    public MessageFrame handlePing(MessageFrame req) {
        String sessionId = normalize(req.get("sessionId"));
        if (sessionId.isBlank()) return error("SESSION_NOT_FOUND", "Missing sessionId");
        try {
            if (!sessions.ping(sessionId)) return error("SESSION_NOT_FOUND", "Session not found");
            return MessageFrame.builder("PONG").put("sessionId", sessionId).build();
        } catch (SQLException e) {
            log.error("PING_ERR session={}", sessionId, e);
            return error("SERVER_ERROR", "Failed to update keepalive");
        }
    }

    public MessageFrame handleLogout(MessageFrame req) {
        String sessionId = normalize(req.get("sessionId"));
        if (sessionId.isBlank()) return MessageFrame.builder("BYE").put("sessionId", "-").build();
        try {
            sessions.delete(sessionId);
            log.info("LOGOUT session={}", sessionId);
            return MessageFrame.builder("BYE").put("sessionId", sessionId).build();
        } catch (SQLException e) {
            log.error("LOGOUT_ERR session={}", sessionId, e);
            return error("SERVER_ERROR", "Failed to close session");
        }
    }

    private String normalize(String v) { return v == null ? "" : v.trim(); }
    private MessageFrame error(String code, String message) {
        return MessageFrame.builder("ERROR").put("code", code).put("message", message).build();
    }
}
