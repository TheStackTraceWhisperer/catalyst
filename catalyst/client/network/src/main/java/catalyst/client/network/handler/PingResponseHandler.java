package catalyst.client.network.handler;

import catalyst.client.network.KeepAliveService;
import catalyst.common.dto.world.PingResponse;
import catalyst.common.network.PacketHandler;
import io.netty.channel.ChannelHandlerContext;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class PingResponseHandler implements PacketHandler<PingResponse> {

  private final KeepAliveService keepAliveService;

  @Override
  public void handle(PingResponse payload, ChannelHandlerContext ctx) {
    log.trace("Received PingResponse code={}", payload.code());
    keepAliveService.handlePong(payload);
  }
}