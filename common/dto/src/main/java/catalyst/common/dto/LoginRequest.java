package catalyst.common.dto;

public record LoginRequest(
    String username,
    String password
) {}
