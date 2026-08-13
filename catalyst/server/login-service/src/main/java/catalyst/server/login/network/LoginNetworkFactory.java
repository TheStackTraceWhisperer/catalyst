package catalyst.server.login.network;

import catalyst.common.network.PacketRegistry;
import catalyst.common.network.PacketType;
import catalyst.server.login.handler.LoginRequestHandler;
import catalyst.server.login.handler.LogoutRequestHandler;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class LoginNetworkFactory {

  @Singleton
  public PacketRegistry packetRegistry(
    LoginRequestHandler loginHandler,
    LogoutRequestHandler logoutHandler
  ) {
    PacketRegistry registry = new PacketRegistry();

    // Explicitly map DTO requests to their handlers using Micronaut dependency injection
    registry.register(PacketType.LOGIN_REQUEST, loginHandler);
    registry.register(PacketType.LOGOUT_REQUEST, logoutHandler);

    return registry;
  }
}