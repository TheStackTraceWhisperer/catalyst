package catalyst.common.dto;

import catalyst.common.network.ResponseCode;
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
