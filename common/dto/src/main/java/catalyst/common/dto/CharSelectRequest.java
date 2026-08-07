package catalyst.common.dto;

public record CharSelectRequest(
    String authToken,
    long characterId
) {}
