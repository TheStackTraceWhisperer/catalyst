package catalyst.server.lobby.network;

import catalyst.common.network.PacketRegistry;
import catalyst.common.network.PacketType;
import catalyst.server.lobby.handler.CharCreateRequestHandler;
import catalyst.server.lobby.handler.CharDeleteRequestHandler;
import catalyst.server.lobby.handler.CharListRequestHandler;
import catalyst.server.lobby.handler.CharSelectRequestHandler;
import catalyst.server.lobby.handler.PlayRequestHandler;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class LobbyNetworkFactory {

  @Singleton
  public PacketRegistry packetRegistry(
    CharCreateRequestHandler charCreateHandler,
    CharDeleteRequestHandler charDeleteHandler,
    CharListRequestHandler charListHandler,
    CharSelectRequestHandler charSelectHandler,
    PlayRequestHandler playHandler
  ) {
    PacketRegistry registry = new PacketRegistry();

    registry.register(PacketType.CHAR_CREATE_REQUEST, (catalyst.common.network.PacketHandler<Object>) (Object) charCreateHandler);
    registry.register(PacketType.CHAR_DELETE_REQUEST, (catalyst.common.network.PacketHandler<Object>) (Object) charDeleteHandler);
    registry.register(PacketType.CHAR_LIST_REQUEST, (catalyst.common.network.PacketHandler<Object>) (Object) charListHandler);
    registry.register(PacketType.CHAR_SELECT_REQUEST, (catalyst.common.network.PacketHandler<Object>) (Object) charSelectHandler);
    registry.register(PacketType.PLAY_REQUEST, (catalyst.common.network.PacketHandler<Object>) (Object) playHandler);

    return registry;
  }
}