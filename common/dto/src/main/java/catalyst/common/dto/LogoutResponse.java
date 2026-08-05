package catalyst.common.dto;

import catalyst.common.network.ResponseCode;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LogoutResponse {
    String sessionId;
    ResponseCode code;
    String message;
}
