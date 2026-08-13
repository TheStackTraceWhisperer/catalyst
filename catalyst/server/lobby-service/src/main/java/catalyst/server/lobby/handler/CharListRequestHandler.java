package catalyst.server.lobby.handler;

import catalyst.common.dto.lobby.CharListRequest;
import catalyst.common.dto.lobby.CharListResponse;
import catalyst.common.dto.lobby.CharacterSummary;
import catalyst.common.network.PacketHandler;
import catalyst.common.network.ResponseCode;
import catalyst.server.lobby.repository.CharacterRepository;
import io.netty.channel.ChannelHandlerContext;
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

    @Override
    public void handle(CharListRequest req, ChannelHandlerContext ctx) {
        long accountId = 1L; // Contextual account ID supplied by Gateway session boundary

        try {
            List<CharacterRepository.CharacterListRow> rows = characters.findActiveByAccount(accountId);
            List<CharacterSummary> characterSummaries = new ArrayList<>(rows.size());

            for (var r : rows) {
                characterSummaries.add(new CharacterSummary(
                  r.id(),
                  r.name(),
                  r.race(),
                  r.mainJob(),
                  1, // Stubbed job level until character_jobs repository is joined
                  r.currentZoneId()
                ));
            }

            log.info("CHAR_LIST_OK account={} count={}", accountId, rows.size());
            ctx.writeAndFlush(new CharListResponse(characterSummaries));
        } catch (SQLException e) {
            log.error("CHAR_LIST_ERR account={}", accountId, e);
            ctx.writeAndFlush(new CharListResponse(ResponseCode.ERROR, "Failed to fetch character list"));
        }
    }
}