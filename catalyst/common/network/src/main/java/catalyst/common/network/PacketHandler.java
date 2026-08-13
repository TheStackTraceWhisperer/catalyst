package catalyst.common.network;

import io.netty.channel.ChannelHandlerContext;

/**
 * The foundational contract for executing game logic.
 * Implementations of this interface are responsible for their own concurrency
 * (e.g., executing immediately, dropping into a Virtual Thread, or a Zone Tick).
 * * @param <T> The strictly-typed DTO payload this handler expects (e.g., LoginRequest)
 */
public interface PacketHandler<T> {

  /**
   * Executes the logic for the given network payload.
   * * @param payload The deserialized object payload.
   * @param ctx The Netty channel context, used to write responses or extract session attributes.
   */
  void handle(T payload, ChannelHandlerContext ctx);
}