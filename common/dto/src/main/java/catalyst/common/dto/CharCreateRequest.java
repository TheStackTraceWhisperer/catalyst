package catalyst.common.dto;

public record CharCreateRequest(
    String authToken,
    String name,
    int race,
    int size,
    int face,
    int mainJob,
    String nation
) {}
