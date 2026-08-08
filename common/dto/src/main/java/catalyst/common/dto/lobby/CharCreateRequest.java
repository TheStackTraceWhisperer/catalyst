package catalyst.common.dto.lobby;

import catalyst.common.network.GatewayFrame;
import catalyst.common.network.GatewayMessage;

public record CharCreateRequest(
    String authToken,
    String name,
    int race,
    int size,
    int face,
    int mainJob,
    String nation
) implements GatewayMessage {

    @Override
    public byte gatewayFlag() {
        return GatewayFrame.FLAG_LOBBY;
    }
}
