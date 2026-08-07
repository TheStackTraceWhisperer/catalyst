package catalyst.common.dto;

public record CharDeleteRequest(
    String authToken,
    long characterId
) {}
