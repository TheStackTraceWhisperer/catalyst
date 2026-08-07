package catalyst.common.dto;

import catalyst.common.network.ResponseCode;
import java.util.List;

public record CharListResponse(
    ResponseCode code,
    List<CharacterDto> characters
) {
    public record CharacterDto(
        String id,
        String name,
        int race,
        String raceName,
        int size,
        int face,
        int mainJob,
        String jobName,
        int nation,
        int zone
    ) {}
}
