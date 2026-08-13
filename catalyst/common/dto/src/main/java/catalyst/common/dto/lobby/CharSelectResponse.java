package catalyst.common.dto.lobby;

import catalyst.common.network.ResponseCode;

/**
 * Server confirmation of character selection.
 *
 * @param code Operation status (Header-First).
 * @param selectedCharacter Full character summary confirmation.
 * @param errorMessage Context on failure (null on success).
 */
public record CharSelectResponse(
  ResponseCode code,
  CharacterSummary selectedCharacter,
  String errorMessage
) {
  public CharSelectResponse(CharacterSummary selectedCharacter) {
    this(ResponseCode.OK, selectedCharacter, null);
  }

  public CharSelectResponse(ResponseCode code, String errorMessage) {
    this(code, null, errorMessage);
  }
}