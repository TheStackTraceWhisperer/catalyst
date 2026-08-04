package catalyst.ffxi.common.net.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LogoutResponse {
    String sessionId;
    String code;
    String message;
}
