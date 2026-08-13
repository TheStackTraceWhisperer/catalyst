package catalyst.client.network;

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
public class ClientChannelInitializer extends ChannelInitializer<QuicStreamChannel> {

  private final PacketRegistry registry;

  @Override
  protected void initChannel(QuicStreamChannel ch) {
    ChannelPipeline pipeline = ch.pipeline();

    // --- INBOUND (Reading from Gateway/Server) ---
    // 1. Wait for complete frame (strip 2-byte length header)
    pipeline.addLast(new LengthFieldBasedFrameDecoder(65535, 0, 2, 0, 2));
    // 2. Read 2-byte Ordinal & Fory payload -> DecodedPacket
    pipeline.addLast(new ForyDecoder());
    // 3. Immediate O(1) invocation of PacketHandler on EventLoop thread
    pipeline.addLast(new InboundPacketHandler(registry));

    // --- OUTBOUND (Writing to Gateway/Server) ---
    // 2. Prepend 2-byte frame length header
    pipeline.addLast(new LengthFieldPrepender(2));
    // 1. DecodedPacket -> write 2-byte Ordinal & Fory payload
    pipeline.addLast(new ForyEncoder());
  }
}