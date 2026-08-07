package catalyst.common.dto;

import catalyst.common.network.GatewayFrame;
import catalyst.common.network.GatewayMessage;
import catalyst.common.network.ResponseCode;

public record CharSelectResponse(
    ResponseCode code,
    String message,
    long characterId,
    String characterName,
    int homeZoneId,
    int currentZoneId,
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
