package catalyst.server.lobby.handler;

import catalyst.common.dto.PlayRequest;
import catalyst.common.dto.PlayResponse;
import catalyst.common.network.GatewayControlMessage;
import catalyst.server.common.network.PacketHandler;
import catalyst.common.network.ResponseCode;
import catalyst.server.common.repository.AuthTicketStore;
import catalyst.server.lobby.properties.ServerProperties;
import catalyst.server.lobby.repository.CharacterRepository;
import catalyst.server.lobby.repository.SessionRepository;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.sql.SQLException;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class PlayRequestHandler implements PacketHandler<PlayRequest> {

    private final CharacterRepository characters;
    private final SessionRepository sessions;
    private final AuthTicketStore tickets;
    private final ServerProperties props;

    @Override
    public Class<PlayRequest> getPacketType() {
        return PlayRequest.class;
    }

    @Override
    public Object handle(PlayRequest req) {
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

            PlayResponse playResponse = new PlayResponse(
                ResponseCode.OK, null, sessionId,
                accountId, charId, id.name(),
                id.currentZoneId(), pop,
                props.getKeepaliveIntervalMs(),
                id.homeZoneId(),
                id.currentX(), id.currentY(), id.currentZ(), id.currentHeading()
            );

            // Return both the control message for the gateway and the play response for the client
            return new Object[] {
                new GatewayControlMessage("play_success", sessionId, "DEFAULT"),
                playResponse
            };
        } catch (SQLException e) {
            log.error("PLAY_ERR account={} charId={}", accountId, charId, e);
            return new PlayResponse(ResponseCode.ERROR, "Failed to enter world", null, -1, -1, null, 0, 0, 5000L, 0, 0f, 0f, 0f, 0f);
        }
    }
}
