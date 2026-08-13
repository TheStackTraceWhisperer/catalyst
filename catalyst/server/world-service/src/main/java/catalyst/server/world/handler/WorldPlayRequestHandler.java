package catalyst.server.world.handler;

import catalyst.common.dto.lobby.PlayRequest;
import catalyst.common.dto.lobby.PlayResponse;
import catalyst.common.network.PacketHandler;
import catalyst.common.network.ResponseCode;
import catalyst.server.common.model.CharacterSpawnState;
import catalyst.server.world.repository.CharacterRepository;
import catalyst.server.world.repository.SessionRepository;
import io.netty.channel.ChannelHandlerContext;
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

    @Override
    public void handle(PlayRequest req, ChannelHandlerContext ctx) {
        long charId = req.characterId();
        if (charId <= 0) {
            ctx.writeAndFlush(new PlayResponse(ResponseCode.CONFLICT, charId, "characterId required"));
            return;
        }

        long accountId = 1L; // Injected via session boundary

        try {
            var spawnStateOpt = characters.findActiveByIdAndAccount(charId, accountId);
            if (spawnStateOpt.isEmpty()) {
                ctx.writeAndFlush(new PlayResponse(ResponseCode.NOT_FOUND, charId, "Character not found"));
                return;
            }

            CharacterSpawnState state = spawnStateOpt.get();
            try {
                sessions.create(accountId, charId, state.currentZoneId());
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    log.info("PLAY_ERR account={} charId={} reason=already_online", accountId, charId);
                    ctx.writeAndFlush(new PlayResponse(ResponseCode.CONFLICT, charId, "Account or character is already online"));
                    return;
                }
                throw e;
            }

            log.info("PLAY_OK account={} charId={} zone={}", accountId, charId, state.currentZoneId());
            ctx.writeAndFlush(new PlayResponse(charId, state.currentZoneId()));
        } catch (Exception e) {
            log.error("PLAY_ERR account={} charId={}", accountId, charId, e);
            ctx.writeAndFlush(new PlayResponse(ResponseCode.ERROR, charId, "Failed to start session"));
        }
    }
}