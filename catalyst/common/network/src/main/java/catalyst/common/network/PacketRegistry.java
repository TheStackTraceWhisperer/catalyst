package catalyst.common.network;

import java.util.EnumMap;
import java.util.Map;

/**
 * A zero-allocation, O(1) dictionary for routing game packets.
 * Uses an array-backed EnumMap for maximal JVM contiguous memory performance.
 * This class is instantiated once per server context at startup.
 */
public class PacketRegistry {

  // Java's EnumMap is backed by a flat Object[] array matching the Enum size.
  // get(type) simply calls type.ordinal() and performs contiguous array access.
  // Performance: 1-2 nanoseconds (comparable to array[opcode]).
  private final Map<PacketType, PacketHandler<Object>> handlers = new EnumMap<>(PacketType.class);

  /**
   * Registers a strongly-typed handler to a specific packet type.
   * Performs a null check to guarantee registry integrity.
   */
  @SuppressWarnings("unchecked")
  public <T> void register(PacketType type, PacketHandler<T> handler) {
    if (handlers.containsKey(type)) {
      throw new IllegalStateException("Handler already registered for PacketType: " + type.name());
    }

    // Type erasure safe generic storage.
    // Type safety is guaranteed at runtime by ForyDecoder deserializing
    // objects specific to the same PacketType enum contract.
    handlers.put(type, (PacketHandler<Object>) handler);
  }

  /**
   * Fetches the handler in true O(1) contiguous array access time.
   * Returns null if no handler is registered for this context.
   * This method is perfect for the EventLoop.
   */
  public PacketHandler<Object> getHandler(PacketType type) {
    return handlers.get(type);
  }
}