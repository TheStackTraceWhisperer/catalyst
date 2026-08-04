package catalyst.ffxi.common.net.dto;

import catalyst.ffxi.common.net.ResponseCode;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LogoutResponse {
    String sessionId;
    ResponseCode code;
    String message;
}
