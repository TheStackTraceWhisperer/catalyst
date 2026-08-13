package catalyst.server.world.network;

import catalyst.common.network.PacketRegistry;
import catalyst.common.network.PacketType;
import catalyst.server.world.handler.WorldLogoutRequestHandler;
import catalyst.server.world.handler.WorldPingRequestHandler;
import catalyst.server.world.handler.WorldPlayRequestHandler;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class WorldNetworkFactory {

  @Singleton
  public PacketRegistry packetRegistry(
    WorldPingRequestHandler pingHandler,
    WorldLogoutRequestHandler logoutHandler,
    WorldPlayRequestHandler playHandler
  ) {
    PacketRegistry registry = new PacketRegistry();

    registry.register(PacketType.PING_REQUEST,pingHandler);
    registry.register(PacketType.LOGOUT_REQUEST, logoutHandler);
    registry.register(PacketType.PLAY_REQUEST, playHandler);

    return registry;
  }
}