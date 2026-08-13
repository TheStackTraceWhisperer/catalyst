package catalyst.common.dto.lobby;

import catalyst.common.network.ResponseCode;
import java.util.List;

/**
 * Server response containing the account's character summaries.
 *
 * @param code Operation status (Header-First).
 * @param characters List of character summaries (empty if none exist or code != OK).
 * @param errorMessage Context on failure (null on success).
 */
public record CharListResponse(
  ResponseCode code,
  List<CharacterSummary> characters,
  String errorMessage
) {
    public CharListResponse(List<CharacterSummary> characters) {
        this(ResponseCode.OK, characters != null ? characters : List.of(), null);
    }

    public CharListResponse(ResponseCode code, String errorMessage) {
        this(code, List.of(), errorMessage);
    }
}