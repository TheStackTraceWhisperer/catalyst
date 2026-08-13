package catalyst.common.network;

import lombok.Getter;

@Getter
public enum PacketType {
  // Control / World
  PING_REQUEST(ServiceType.CONTROL),
  PING_RESPONSE(ServiceType.CONTROL),

  // Login Service
  LOGIN_REQUEST(ServiceType.LOGIN),
  LOGIN_RESPONSE(ServiceType.LOGIN),
  LOGOUT_REQUEST(ServiceType.LOGIN),
  LOGOUT_RESPONSE(ServiceType.LOGIN),

  // Lobby Service
  CHAR_LIST_REQUEST(ServiceType.LOBBY),
  CHAR_LIST_RESPONSE(ServiceType.LOBBY),
  CHAR_CREATE_REQUEST(ServiceType.LOBBY),
  CHAR_CREATE_RESPONSE(ServiceType.LOBBY),
  CHAR_DELETE_REQUEST(ServiceType.LOBBY),
  CHAR_DELETE_RESPONSE(ServiceType.LOBBY),
  CHAR_SELECT_REQUEST(ServiceType.LOBBY),
  CHAR_SELECT_RESPONSE(ServiceType.LOBBY),

  // World Service
  PLAY_REQUEST(ServiceType.WORLD),
  PLAY_RESPONSE(ServiceType.WORLD);

  /**
   * Caches PacketType.values() once at class-load time to avoid allocating
   * a new array on every network lookup.
   */
  private static final PacketType[] VALUES = values();

  private final ServiceType targetService;

  PacketType(ServiceType targetService) {
    this.targetService = targetService;
  }

  /**
   * Resolves a PacketType from its wire OpCode ID in O(1) time without heap allocation.
   *
   * @param wireId The 2-byte OpCode ID received from the network.
   * @return The corresponding PacketType, or null if the wireId is invalid.
   */
  public static PacketType fromWireId(int wireId) {
    if (wireId < 0 || wireId >= VALUES.length) {
      return null;
    }
    return VALUES[wireId];
  }
}