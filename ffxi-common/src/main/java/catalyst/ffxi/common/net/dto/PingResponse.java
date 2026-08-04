package catalyst.ffxi.common.net.dto;

import catalyst.ffxi.common.net.ResponseCode;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PingResponse {
    String type; // e.g. PONG or ERROR
    String sessionId;
    ResponseCode code;
    String message;
}
