package catalyst.common.dto.lobby;

import catalyst.common.network.ResponseCode;

/**
 * Server response to a character deletion request.
 *
 * @param code Operation status (OK, NOT_FOUND, ERROR).
 * @param characterId The deleted character ID.
 * @param errorMessage Context on failure (null on success).
 */
public record CharDeleteResponse(
  ResponseCode code,
  long characterId,
  String errorMessage
) {
  public CharDeleteResponse(long characterId) {
    this(ResponseCode.OK, characterId, null);
  }

  public CharDeleteResponse(ResponseCode code, long characterId, String errorMessage) {
    this.code = code;
    this.characterId = characterId;
    this.errorMessage = errorMessage;
  }
}