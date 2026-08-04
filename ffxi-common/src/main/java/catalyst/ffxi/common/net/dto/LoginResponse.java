package catalyst.ffxi.common.net.dto;

import catalyst.ffxi.common.net.ResponseCode;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LoginResponse {
    ResponseCode code;
    String message;
    String authToken;
    long accountId;
}
