package catalyst.server.world.handler;

import catalyst.common.dto.world.LogoutRequest;
import catalyst.common.dto.world.LogoutResponse;
import catalyst.common.network.ForySerializer;
import catalyst.common.network.PacketHandler;
import catalyst.common.network.ResponseCode;
import catalyst.common.network.ServiceType;
import catalyst.server.common.network.GatewayControlMessage;
import catalyst.server.common.network.GatewayFrame;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class WorldLogoutRequestHandler implements PacketHandler<LogoutRequest> {

    @Override
    public void handle(LogoutRequest req, ChannelHandlerContext ctx) {
        try {
            // 1. Emit GatewayControlMessage("logout_success") so Gateway resets session state
            GatewayControlMessage controlSignal = new GatewayControlMessage("logout_success");
            byte[] controlBytes = ForySerializer.serialize(controlSignal);
            ctx.write(new GatewayFrame(ServiceType.CONTROL, "", controlBytes));

            // 2. Return LogoutResponse
            ctx.writeAndFlush(new LogoutResponse(ResponseCode.OK, null));
        } catch (Exception e) {
            log.error("Failed to process logout request", e);
            ctx.writeAndFlush(new LogoutResponse(ResponseCode.ERROR, "Failed to terminate session"));
        }
    }
}