package catalyst.common.dto;

import catalyst.common.network.ResponseCode;
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
