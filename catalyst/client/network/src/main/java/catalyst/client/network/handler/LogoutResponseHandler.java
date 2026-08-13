package catalyst.client.network.handler;

import catalyst.client.network.KeepAliveService;
import catalyst.client.network.listener.SessionNetworkListener;
import catalyst.common.dto.world.LogoutResponse;
import catalyst.common.network.PacketHandler;
import catalyst.common.network.ResponseCode;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class LogoutResponseHandler implements PacketHandler<LogoutResponse> {

  private final KeepAliveService keepAliveService;
  private final SessionNetworkListener sessionListener;

  @Override
  public void handle(LogoutResponse payload, ChannelHandlerContext ctx) {
    if (payload.code() == ResponseCode.OK) {
      keepAliveService.stop();
      sessionListener.onLoggedOut();
    }
  }
}