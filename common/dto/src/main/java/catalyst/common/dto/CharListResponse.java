package catalyst.common.dto;

import catalyst.common.network.ResponseCode;
import lombok.Builder;
import lombok.Value;
import java.util.List;

@Value
@Builder
public class CharListResponse {
    ResponseCode code;
    List<CharacterDto> characters;

    @Value
    @Builder
    public static class CharacterDto {
        String id;
        String name;
        int race;
        String raceName;
        int size;
        int face;
        int mainJob;
        String jobName;
        int nation;
        int zone;
    }
}
