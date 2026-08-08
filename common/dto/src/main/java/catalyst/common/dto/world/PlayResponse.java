package catalyst.common.dto.world;

import catalyst.common.network.GatewayFrame;
import catalyst.common.network.GatewayMessage;
import catalyst.common.network.ResponseCode;

public record PlayResponse(
    ResponseCode code,
    String message,
    String sessionId,
    long accountId,
    long characterId,
    String characterName,
    int zoneId,
    int playersInZone,
    long keepaliveIntervalMs,
    int homeZoneId,
    float x,
    float y,
    float z,
    float rot
) implements GatewayMessage {

    @Override
    public byte gatewayFlag() {
        return GatewayFrame.FLAG_LOBBY;
    }
}
