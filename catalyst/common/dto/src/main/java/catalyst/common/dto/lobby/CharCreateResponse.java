package catalyst.common.dto.lobby;

import catalyst.common.network.ResponseCode;

public record CharCreateResponse(
    ResponseCode code,
    Long characterId,
    String errorMessage
) {
  public CharCreateResponse(CharCreateResponse characterId) {
    this(ResponseCode.OK, characterId.characterId, null);
  }
  public CharCreateResponse(ResponseCode code, String errorMessage) {
    this(code, null, errorMessage);
  }
}
