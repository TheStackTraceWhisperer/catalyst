package catalyst.common.dto;

import catalyst.common.network.ResponseCode;

public record CharCreateResponse(
    ResponseCode code,
    String message,
    long characterId,
    String name
) {}
