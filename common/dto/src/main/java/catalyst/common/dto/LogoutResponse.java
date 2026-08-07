package catalyst.common.dto;

import catalyst.common.network.ResponseCode;

public record LogoutResponse(
    String sessionId,
    ResponseCode code,
    String message
) {}
