package catalyst.ffxi.common.net.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PingResponse {
    String type; // e.g. PONG or ERROR
    String sessionId;
    String code;
    String message;
}
