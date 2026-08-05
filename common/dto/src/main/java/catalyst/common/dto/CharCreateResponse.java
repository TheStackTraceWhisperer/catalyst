package catalyst.common.dto;

import catalyst.common.network.ResponseCode;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CharCreateResponse {
    ResponseCode code;
    String message;
    long characterId;
    String name;
}
