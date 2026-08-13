package catalyst.client.network.handler;

import catalyst.client.network.listener.LobbyNetworkListener;
import catalyst.common.dto.lobby.CharDeleteResponse;
import catalyst.common.network.PacketHandler;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class CharDeleteResponseHandler implements PacketHandler<CharDeleteResponse> {

  private final LobbyNetworkListener lobbyListener;

  @Override
  public void handle(CharDeleteResponse payload, ChannelHandlerContext ctx) {
    lobbyListener.onCharacterDeleted(
      payload.code(),
      payload.characterId(),
      payload.errorMessage()
    );
  }
}