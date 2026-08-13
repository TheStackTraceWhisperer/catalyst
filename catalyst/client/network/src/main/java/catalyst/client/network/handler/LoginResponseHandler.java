package catalyst.client.network.handler;

import catalyst.client.network.listener.SessionNetworkListener;
import catalyst.common.dto.login.LoginResponse;
import catalyst.common.network.PacketHandler;
import catalyst.common.network.ResponseCode;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class LoginResponseHandler implements PacketHandler<LoginResponse> {

  private final SessionNetworkListener sessionListener;

  @Override
  public void handle(LoginResponse payload, ChannelHandlerContext ctx) {
    if (payload.code() == ResponseCode.OK && payload.accountId() != null) {
      sessionListener.onAuthenticated(payload.accountId());
    } else {
      sessionListener.onAuthenticationFailed(payload.code(), payload.errorMessage());
    }
  }
}