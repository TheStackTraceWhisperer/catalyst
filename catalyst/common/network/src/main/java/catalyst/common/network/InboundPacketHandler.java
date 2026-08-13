package catalyst.common.network;

import catalyst.common.network.DecodedPacket;
import catalyst.common.network.PacketHandler;
import catalyst.common.network.PacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * Universal terminal Netty handler for inbound packets.
 * Invokes the registered PacketHandler directly on the EventLoop thread.
 */
@Slf4j
public class InboundPacketHandler extends SimpleChannelInboundHandler<DecodedPacket> {

  private final PacketRegistry registry;

  public InboundPacketHandler(PacketRegistry registry) {
    this.registry = registry;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, DecodedPacket msg) {
    PacketHandler<Object> handler = registry.getHandler(msg.type());

    if (handler != null) {
      // Immediate inline execution on the EventLoop thread.
      // The handler itself is responsible for any thread-shifting if required.
      handler.handle(msg.payload(), ctx);
    } else {
      log.warn("Dropped unhandled packet type: {}", msg.type());
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    log.error("Exception in client inbound pipeline", cause);
    ctx.close();
  }
}