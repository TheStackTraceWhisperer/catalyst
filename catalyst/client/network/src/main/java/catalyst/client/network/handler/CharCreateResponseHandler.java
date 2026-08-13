package catalyst.client.network.handler;

import catalyst.client.network.listener.LobbyNetworkListener;
import catalyst.common.dto.lobby.CharCreateResponse;
import catalyst.common.network.PacketHandler;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class CharCreateResponseHandler implements PacketHandler<CharCreateResponse> {

  private final LobbyNetworkListener lobbyListener;

  @Override
  public void handle(CharCreateResponse payload, ChannelHandlerContext ctx) {
    lobbyListener.onCharacterCreated(
      payload.code(),
      payload.characterId(),
      payload.errorMessage()
    );
  }
}