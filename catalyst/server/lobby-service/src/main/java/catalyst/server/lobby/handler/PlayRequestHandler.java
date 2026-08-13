package catalyst.server.lobby.handler;

import catalyst.common.dto.lobby.PlayRequest;
import catalyst.common.dto.lobby.PlayResponse;
import catalyst.common.network.ForySerializer;
import catalyst.server.common.network.GatewayControlMessage;
import catalyst.server.common.network.GatewayFrame;
import catalyst.common.network.PacketHandler;
import catalyst.common.network.ResponseCode;
import catalyst.common.network.ServiceType;
import catalyst.server.lobby.service.CharacterService;
import catalyst.server.lobby.service.WorldRegistryService;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class PlayRequestHandler implements PacketHandler<PlayRequest> {

  private final CharacterService characterService;
  private final WorldRegistryService worldRegistry;

  @Override
  public void handle(PlayRequest payload, ChannelHandlerContext ctx) {
    long characterId = payload.characterId();
    log.info("Processing play request for characterId={}", characterId);

    characterService.getCharacterZoneIdAsync(characterId)
      .thenAccept(targetZoneId -> {
        if (targetZoneId == null) {
          ctx.writeAndFlush(new PlayResponse(ResponseCode.NOT_FOUND, characterId, "Character not found"));
          return;
        }

        String worldAddress = worldRegistry.resolveWorldServerAddress(targetZoneId);
        if (worldAddress == null) {
          log.error("No active world server instance found for zoneId={}", targetZoneId);
          ctx.writeAndFlush(new PlayResponse(ResponseCode.ERROR, characterId, "Target zone server offline"));
          return;
        }

        log.info("Play authorized for characterId={} entering zoneId={} at address={}",
          characterId, targetZoneId, worldAddress);

        // 1. Emit GatewayControlMessage("play_success") so Gateway transitions session
        GatewayControlMessage controlSignal = new GatewayControlMessage(
          "play_success",
          worldAddress,
          String.valueOf(characterId)
        );
        writeControlFrame(ctx, controlSignal);

        // 2. Return PlayResponse to client
        PlayResponse response = new PlayResponse(characterId, targetZoneId);
        ctx.writeAndFlush(response);
      })
      .exceptionally(err -> {
        log.error("Internal error processing play request for characterId={}", characterId, err);
        ctx.writeAndFlush(new PlayResponse(ResponseCode.ERROR, characterId, "Internal server error"));
        return null;
      });
  }

  private void writeControlFrame(ChannelHandlerContext ctx, GatewayControlMessage controlMsg) {
    try {
      byte[] controlBytes = ForySerializer.serialize(controlMsg);
      GatewayFrame controlFrame = new GatewayFrame(ServiceType.CONTROL, "", controlBytes);
      ctx.write(controlFrame);
    } catch (Exception e) {
      log.error("Failed to serialize GatewayControlMessage on play request", e);
    }
  }
}