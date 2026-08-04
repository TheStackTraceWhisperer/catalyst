package catalyst.ffxi.common.net.dto;

import catalyst.ffxi.common.net.ResponseCode;
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
