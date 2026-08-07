package catalyst.common.dto;

import catalyst.common.network.ResponseCode;

public record PingResponse(
    /** e.g. "PONG" or "ERROR" */
    String type,
    String sessionId,
    ResponseCode code,
    String message
) {}
