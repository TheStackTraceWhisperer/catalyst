package catalyst.server.login.handler;

import catalyst.common.dto.world.LogoutRequest;
import catalyst.common.dto.world.LogoutResponse;
import catalyst.server.common.network.GatewayControlMessage;
import catalyst.server.common.network.GatewayFrame;
import catalyst.common.network.PacketHandler;
import catalyst.common.network.ResponseCode;
import catalyst.common.network.ServiceType;
import catalyst.common.network.ForySerializer;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class LogoutRequestHandler implements PacketHandler<LogoutRequest> {

  @Override
  public void handle(LogoutRequest payload, ChannelHandlerContext ctx) {
    log.info("Processing logout request");

    // 1. Emit GatewayControlMessage("logout_success") to reset Gateway state
    GatewayControlMessage controlSignal = new GatewayControlMessage("logout_success");
    writeControlFrame(ctx, controlSignal);

    // 2. Return LogoutResponse
    LogoutResponse response = new LogoutResponse(ResponseCode.OK, null);
    ctx.writeAndFlush(response);
  }

  private void writeControlFrame(ChannelHandlerContext ctx, GatewayControlMessage controlMsg) {
    try {
      byte[] controlBytes = ForySerializer.serialize(controlMsg);
      GatewayFrame controlFrame = new GatewayFrame(ServiceType.CONTROL, "", controlBytes);
      ctx.writeAndFlush(controlFrame);
    } catch (Exception e) {
      log.error("Failed to serialize GatewayControlMessage on logout", e);
    }
  }
}