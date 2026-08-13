package catalyst.server.lobby.handler;

import catalyst.common.dto.lobby.CharSelectRequest;
import catalyst.common.dto.lobby.CharSelectResponse;
import catalyst.common.dto.lobby.CharacterSummary;
import catalyst.common.network.PacketHandler;
import catalyst.common.network.ResponseCode;
import catalyst.server.lobby.repository.CharacterRepository;
import catalyst.server.lobby.repository.SessionRepository;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.List;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CharSelectRequestHandler implements PacketHandler<CharSelectRequest> {

    private final CharacterRepository characters;
    private final SessionRepository sessions;

    @Override
    public void handle(CharSelectRequest req, ChannelHandlerContext ctx) {
        long charId = req.characterId();
        if (charId <= 0) {
            ctx.writeAndFlush(new CharSelectResponse(ResponseCode.CONFLICT, "characterId required"));
            return;
        }

        long accountId = 1L; // Contextual account ID supplied by Gateway session boundary

        try {
            if (sessions.hasActiveSession(accountId)) {
                ctx.writeAndFlush(new CharSelectResponse(ResponseCode.CONFLICT, "Account is already online"));
                return;
            }

            List<CharacterRepository.CharacterListRow> rows = characters.findActiveByAccount(accountId);
            var summaryRow = rows.stream().filter(r -> r.id() == charId).findFirst();

            if (summaryRow.isEmpty()) {
                ctx.writeAndFlush(new CharSelectResponse(ResponseCode.NOT_FOUND, "Character not found"));
                return;
            }

            var r = summaryRow.get();
            CharacterSummary selectedSummary = new CharacterSummary(
              r.id(),
              r.name(),
              r.race(),
              r.mainJob(),
              1, // Stubbed job level
              r.currentZoneId()
            );

            log.info("CHAR_SELECT_OK account={} characterId={} zone={}", accountId, charId, r.currentZoneId());
            ctx.writeAndFlush(new CharSelectResponse(selectedSummary));
        } catch (SQLException e) {
            log.error("CHAR_SELECT_ERR account={} charId={}", accountId, charId, e);
            ctx.writeAndFlush(new CharSelectResponse(ResponseCode.ERROR, "Failed to load character"));
        }
    }
}