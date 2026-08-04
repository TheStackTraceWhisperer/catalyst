package catalyst.ffxi.common.net.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CharCreateResponse {
    String code;
    String message;
    long characterId;
    String name;
}
