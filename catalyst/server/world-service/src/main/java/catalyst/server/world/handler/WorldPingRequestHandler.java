package catalyst.server.world.handler;

import catalyst.common.dto.world.PingRequest;
import catalyst.common.dto.world.PingResponse;
import catalyst.common.network.PacketHandler;
import catalyst.common.network.ResponseCode;
import catalyst.server.world.repository.SessionRepository;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class WorldPingRequestHandler implements PacketHandler<PingRequest> {

    private final SessionRepository sessions;

    @Override
    public void handle(PingRequest req, ChannelHandlerContext ctx) {
        // Echo back client timestamp with OK status
        ctx.writeAndFlush(new PingResponse(ResponseCode.OK, req.timestamp()));
    }
}