package catalyst.server.lobby.network;

import catalyst.common.network.*;
import catalyst.server.common.network.GatewayFrameDecoder;
import catalyst.server.common.network.GatewayFrameEncoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class ServerChannelInitializer extends ChannelInitializer<QuicStreamChannel> {

  private final PacketRegistry packetRegistry;

  @Override
  protected void initChannel(QuicStreamChannel ch) {
    ChannelPipeline pipeline = ch.pipeline();

    // 1. Unpack internal GatewayFrame envelope from the Gateway proxy
    pipeline.addLast(new GatewayFrameDecoder());
    pipeline.addLast(new GatewayFrameEncoder());

    // 2. Outbound encoding chain back to Gateway / Client
    pipeline.addLast(new LengthFieldPrepender(2));
    pipeline.addLast(new ForyEncoder());

    // 3. Unpack length-prefixed binary Apache Fory stream into DecodedPacket
    pipeline.addLast(new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));
    pipeline.addLast(new ForyDecoder());

    // 4. O(1) terminal packet handler router
    pipeline.addLast(new InboundPacketHandler(packetRegistry));
  }
}