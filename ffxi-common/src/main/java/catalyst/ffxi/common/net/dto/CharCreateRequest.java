package catalyst.ffxi.common.net.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CharCreateRequest {
    String authToken;
    String name;
    int race;
    int size;
    int face;
    int mainJob;
    String nation;
}
