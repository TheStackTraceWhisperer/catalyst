package catalyst.server.world.handler;

import catalyst.common.dto.PlayRequest;
import catalyst.common.dto.PlayResponse;
import catalyst.server.common.network.PacketHandler;
import catalyst.common.network.ResponseCode;
import catalyst.server.common.repository.AuthTicketStore;
import catalyst.server.world.properties.ServerProperties;
import catalyst.server.world.repository.CharacterRepository;
import catalyst.server.world.repository.SessionRepository;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.sql.SQLException;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class WorldPlayRequestHandler implements PacketHandler<PlayRequest> {

    private final CharacterRepository characters;
    private final SessionRepository sessions;
    private final AuthTicketStore tickets;
    private final ServerProperties props;

    @Override
    public Class<PlayRequest> getPacketType() {
        return PlayRequest.class;
    }

    @Override
    public Object handle(PlayRequest req, String sessionId) {
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
            String newSessionId;
            try {
                newSessionId = sessions.create(accountId, charId, id.currentZoneId());
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    log.info("PLAY_ERR account={} charId={} reason=already_online", accountId, charId);
                    return new PlayResponse(ResponseCode.CONFLICT, "Account or character is already online", null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
                }
                throw e;
            }
            int pop = sessions.getZonePopulation(id.currentZoneId());
            log.info("PLAY_OK account={} charId={} session={} zone={} pop={}", accountId, charId, newSessionId, id.currentZoneId(), pop);

            return new PlayResponse(
                ResponseCode.OK, null, newSessionId,
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
}
