package catalyst.ffxi.common.net.dto;

import catalyst.ffxi.common.net.ResponseCode;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CharSelectResponse {
    ResponseCode code;
    String message;
    long characterId;
    String characterName;
    int homeZoneId;
    int currentZoneId;
    float x;
    float y;
    float z;
    float rot;
}
