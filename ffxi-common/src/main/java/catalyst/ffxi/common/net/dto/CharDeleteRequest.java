package catalyst.ffxi.common.net.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CharDeleteRequest {
    String authToken;
    long characterId;
}
