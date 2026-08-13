package catalyst.client.network.handler;

import catalyst.client.network.listener.LobbyNetworkListener;
import catalyst.common.dto.lobby.PlayResponse;
import catalyst.common.network.PacketHandler;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class PlayResponseHandler implements PacketHandler<PlayResponse> {

  private final LobbyNetworkListener lobbyListener;

  @Override
  public void handle(PlayResponse payload, ChannelHandlerContext ctx) {
    lobbyListener.onWorldBound(
      payload.code(),
      payload.characterId(),
      payload.targetZoneId(),
      payload.errorMessage()
    );
  }
}