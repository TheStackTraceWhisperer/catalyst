package catalyst.common.dto.lobby;

import catalyst.common.network.ResponseCode;

/**
 * Server response authorizing world transition.
 * Instructs Gateway to bind character ID to ClientSession and provides world endpoint details.
 *
 * @param code Operation status (Header-First).
 * @param characterId Character ID entering the world.
 * @param targetZoneId Initial target zone ID for world server routing.
 * @param errorMessage Context on failure (null on success).
 */
public record PlayResponse(
  ResponseCode code,
  long characterId,
  int targetZoneId,
  String errorMessage
) {
  public PlayResponse(long characterId, int targetZoneId) {
    this(ResponseCode.OK, characterId, targetZoneId, null);
  }

  public PlayResponse(ResponseCode code, long characterId, String errorMessage) {
    this(code, characterId, 0, errorMessage);
  }
}