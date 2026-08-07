package catalyst.common.dto;

import catalyst.common.network.ResponseCode;

public record LoginResponse(
    ResponseCode code,
    String message,
    String authToken,
    long accountId
) {}
