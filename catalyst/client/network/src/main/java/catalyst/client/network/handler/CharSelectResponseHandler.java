package catalyst.client.network.handler;

import catalyst.client.network.listener.LobbyNetworkListener;
import catalyst.common.dto.lobby.CharSelectResponse;
import catalyst.common.network.PacketHandler;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class CharSelectResponseHandler implements PacketHandler<CharSelectResponse> {

  private final LobbyNetworkListener lobbyListener;

  @Override
  public void handle(CharSelectResponse payload, ChannelHandlerContext ctx) {
    lobbyListener.onCharacterSelected(
      payload.code(),
      payload.selectedCharacter(),
      payload.errorMessage()
    );
  }
}