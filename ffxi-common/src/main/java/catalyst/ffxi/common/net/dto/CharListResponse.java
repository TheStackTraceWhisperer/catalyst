package catalyst.ffxi.common.net.dto;

import catalyst.ffxi.common.net.ResponseCode;
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
