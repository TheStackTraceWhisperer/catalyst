package catalyst.server.lobby.handler;

import catalyst.common.dto.CharListRequest;
import catalyst.common.dto.CharListResponse;
import catalyst.server.common.network.PacketHandler;
import catalyst.common.network.ResponseCode;
import catalyst.server.common.repository.AuthTicketStore;
import catalyst.server.lobby.repository.CharacterRepository;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CharListRequestHandler implements PacketHandler<CharListRequest> {

    private final CharacterRepository characters;
    private final AuthTicketStore tickets;

    @Override
    public Class<CharListRequest> getPacketType() {
        return CharListRequest.class;
    }

    @Override
    public CharListResponse handle(CharListRequest req) {
        Long accountId = tickets.validate(req.authToken());
        if (accountId == null) {
            return new CharListResponse(ResponseCode.UNAUTHORIZED, new ArrayList<>());
        }
        try {
            List<CharacterRepository.CharacterListRow> rows = characters.findActiveByAccount(accountId);
            List<CharListResponse.CharacterDto> characterDtos = new ArrayList<>(rows.size());
            for (var r : rows) {
                characterDtos.add(new CharListResponse.CharacterDto(
                    Long.toString(r.id()),
                    r.name(),
                    r.race(),
                    r.raceName(),
                    r.size(),
                    r.face(),
                    r.mainJob(),
                    r.jobName(),
                    r.nation(),
                    r.currentZoneId()
                ));
            }
            log.info("CHAR_LIST_OK account={} count={}", accountId, rows.size());
            return new CharListResponse(ResponseCode.OK, characterDtos);
        } catch (SQLException e) {
            log.error("CHAR_LIST_ERR account={}", accountId, e);
            return new CharListResponse(ResponseCode.ERROR, new ArrayList<>());
        }
    }
}
