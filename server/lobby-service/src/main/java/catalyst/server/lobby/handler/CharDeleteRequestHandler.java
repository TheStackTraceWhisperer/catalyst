package catalyst.server.lobby.handler;

import catalyst.common.dto.CharDeleteRequest;
import catalyst.common.dto.CharDeleteResponse;
import catalyst.server.common.network.PacketHandler;
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
public class CharDeleteRequestHandler implements PacketHandler<CharDeleteRequest> {

    private final CharacterRepository characters;
    private final SessionRepository sessions;
    private final AuthTicketStore tickets;

    @Override
    public Class<CharDeleteRequest> getPacketType() {
        return CharDeleteRequest.class;
    }

    @Override
    public CharDeleteResponse handle(CharDeleteRequest req) {
        Long accountId = tickets.validate(req.authToken());
        if (accountId == null) {
            return new CharDeleteResponse(ResponseCode.UNAUTHORIZED, "Invalid or expired auth token", -1);
        }
        long charId = req.characterId();
        if (charId <= 0) {
            return new CharDeleteResponse(ResponseCode.CONFLICT, "characterId required", -1);
        }
        try {
            if (sessions.characterHasActiveSession(charId)) {
                return new CharDeleteResponse(ResponseCode.CONFLICT, "Character is currently online", charId);
            }
            if (!characters.softDelete(charId, accountId)) {
                return new CharDeleteResponse(ResponseCode.NOT_FOUND, "Character not found", charId);
            }
            log.info("CHAR_DELETE_OK account={} characterId={}", accountId, charId);
            return new CharDeleteResponse(ResponseCode.OK, null, charId);
        } catch (SQLException e) {
            log.error("CHAR_DELETE_ERR account={} characterId={}", accountId, charId, e);
            return new CharDeleteResponse(ResponseCode.ERROR, "Failed to delete character", charId);
        }
    }
}
