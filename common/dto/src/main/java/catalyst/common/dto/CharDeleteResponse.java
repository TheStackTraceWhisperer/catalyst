package catalyst.common.dto;

import catalyst.common.network.ResponseCode;

public record CharDeleteResponse(
    ResponseCode code,
    String message,
    long characterId
) {}
