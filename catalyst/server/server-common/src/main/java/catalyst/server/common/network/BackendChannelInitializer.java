package catalyst.server.common.network;

import catalyst.common.network.ForyDecoder;
import catalyst.common.network.ForyEncoder;
import catalyst.common.network.InboundPacketHandler;
import catalyst.common.network.PacketRegistry;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BackendChannelInitializer extends ChannelInitializer<QuicStreamChannel> {

  private final PacketRegistry packetRegistry;

  @Override
  protected void initChannel(QuicStreamChannel ch) {
    ChannelPipeline pipeline = ch.pipeline();

    // 1. Unpack GatewayFrame wrapper from Gateway proxy
    pipeline.addLast(new GatewayFrameDecoder());
    pipeline.addLast(new GatewayFrameEncoder());

    // 2. Unpack inner DecodedPacket over Fory
    pipeline.addLast(new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));
    pipeline.addLast(new ForyDecoder());

    // 3. Shared O(1) Terminal Netty Handler
    pipeline.addLast(new InboundPacketHandler(packetRegistry));

    // 4. Outbound Response Encoding
    pipeline.addLast(new LengthFieldPrepender(2));
    pipeline.addLast(new ForyEncoder());
  }
}