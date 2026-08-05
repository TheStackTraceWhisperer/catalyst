package catalyst.common.dto;

import catalyst.common.network.ResponseCode;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CharDeleteResponse {
    ResponseCode code;
    String message;
    long characterId;
}
