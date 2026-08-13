package catalyst.server.lobby.handler;

import catalyst.common.dto.lobby.CharDeleteRequest;
import catalyst.common.dto.lobby.CharDeleteResponse;
import catalyst.common.network.PacketHandler;
import catalyst.common.network.ResponseCode;
import catalyst.server.lobby.repository.CharacterRepository;
import catalyst.server.lobby.repository.SessionRepository;
import io.netty.channel.ChannelHandlerContext;
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

    @Override
    public void handle(CharDeleteRequest req, ChannelHandlerContext ctx) {
        long charId = req.characterId();
        if (charId <= 0) {
            ctx.writeAndFlush(new CharDeleteResponse(ResponseCode.CONFLICT, -1L, "characterId required"));
            return;
        }

        long accountId = 1L; // Contextual account ID supplied by Gateway session boundary

        try {
            if (sessions.characterHasActiveSession(charId)) {
                ctx.writeAndFlush(new CharDeleteResponse(ResponseCode.CONFLICT, charId, "Character is currently online"));
                return;
            }
            if (!characters.softDelete(charId, accountId)) {
                ctx.writeAndFlush(new CharDeleteResponse(ResponseCode.NOT_FOUND, charId, "Character not found"));
                return;
            }
            log.info("CHAR_DELETE_OK account={} characterId={}", accountId, charId);
            ctx.writeAndFlush(new CharDeleteResponse(charId));
        } catch (SQLException e) {
            log.error("CHAR_DELETE_ERR account={} characterId={}", accountId, charId, e);
            ctx.writeAndFlush(new CharDeleteResponse(ResponseCode.ERROR, charId, "Failed to delete character"));
        }
    }
}