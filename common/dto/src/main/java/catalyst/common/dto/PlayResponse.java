package catalyst.common.dto;

import catalyst.common.network.ResponseCode;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PlayResponse {
    ResponseCode code;
    String message;
    String sessionId;
    long accountId;
    long characterId;
    String characterName;
    int zoneId;
    int playersInZone;
    long keepaliveIntervalMs;
    int homeZoneId;
    float x;
    float y;
    float z;
    float rot;
}
