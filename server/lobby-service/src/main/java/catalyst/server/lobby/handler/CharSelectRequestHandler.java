package catalyst.server.lobby.handler;

import catalyst.common.dto.CharSelectRequest;
import catalyst.common.dto.CharSelectResponse;
import catalyst.common.network.PacketHandler;
import catalyst.common.network.ResponseCode;
import catalyst.server.common.repository.AuthTicketStore;
import catalyst.server.lobby.repository.CharacterRepository;
import catalyst.server.lobby.repository.SessionRepository;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.sql.SQLException;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CharSelectRequestHandler implements PacketHandler<CharSelectRequest> {

    private final CharacterRepository characters;
    private final SessionRepository sessions;
    private final AuthTicketStore tickets;

    @Override
    public Class<CharSelectRequest> getPacketType() {
        return CharSelectRequest.class;
    }

    @Override
    public CharSelectResponse handle(CharSelectRequest req) {
        Long accountId = tickets.validate(req.authToken());
        if (accountId == null) {
            return new CharSelectResponse(ResponseCode.UNAUTHORIZED, "Invalid or expired auth token", -1, null, 0, 0, 0f, 0f, 0f, 0f);
        }
        long charId = req.characterId();
        if (charId <= 0) {
            return new CharSelectResponse(ResponseCode.CONFLICT, "characterId required", -1, null, 0, 0, 0f, 0f, 0f, 0f);
        }
        try {
            if (sessions.hasActiveSession(accountId)) {
                return new CharSelectResponse(ResponseCode.CONFLICT, "Account is already online", -1, null, 0, 0, 0f, 0f, 0f, 0f);
            }
            var identity = characters.findActiveByIdAndAccount(charId, accountId);
            if (identity.isEmpty()) {
                return new CharSelectResponse(ResponseCode.NOT_FOUND, "Character not found", -1, null, 0, 0, 0f, 0f, 0f, 0f);
            }
            var id = identity.get();
            log.info("CHAR_SELECT_OK account={} characterId={} zone={}", accountId, charId, id.currentZoneId());

            return new CharSelectResponse(
                ResponseCode.OK, null, charId, id.name(),
                id.homeZoneId(), id.currentZoneId(),
                id.currentX(), id.currentY(), id.currentZ(), id.currentHeading()
            );
        } catch (SQLException e) {
            log.error("CHAR_SELECT_ERR account={} charId={}", accountId, charId, e);
            return new CharSelectResponse(ResponseCode.ERROR, "Failed to load character", -1, null, 0, 0, 0f, 0f, 0f, 0f);
        }
    }
}
