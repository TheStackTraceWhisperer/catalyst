package catalyst.common.dto;

public record PlayRequest(
    String authToken,
    long characterId
) {}
