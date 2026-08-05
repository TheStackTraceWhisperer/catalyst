package catalyst.common.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CharSelectRequest {
    String authToken;
    long characterId;
}
