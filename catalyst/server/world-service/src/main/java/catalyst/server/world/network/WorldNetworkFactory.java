package catalyst.server.world.network;

import catalyst.common.network.PacketRegistry;
import catalyst.common.network.PacketType;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class WorldNetworkFactory {

  @Singleton
  public PacketRegistry packetRegistry(

  ) {
    PacketRegistry registry = new PacketRegistry();

    // Explicitly map DTO requests to their handlers using Micronaut dependency injection
//    registry.register(PacketType.LOGIN_REQUEST, loginHandler);
//    registry.register(PacketType.LOGOUT_REQUEST, logoutHandler);

    return registry;
  }
}