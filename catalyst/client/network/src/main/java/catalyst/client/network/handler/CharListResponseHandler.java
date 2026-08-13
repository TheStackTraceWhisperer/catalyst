package catalyst.client.network.handler;

import catalyst.client.network.listener.LobbyNetworkListener;
import catalyst.common.dto.lobby.CharListResponse;
import catalyst.common.network.PacketHandler;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class CharListResponseHandler implements PacketHandler<CharListResponse> {

  private final LobbyNetworkListener lobbyListener;

  @Override
  public void handle(CharListResponse payload, ChannelHandlerContext ctx) {
    lobbyListener.onCharacterListReceived(
      payload.code(),
      payload.characters(),
      payload.errorMessage()
    );
  }
}