package catalyst.common.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PlayRequest {
    String authToken;
    long characterId;
}
