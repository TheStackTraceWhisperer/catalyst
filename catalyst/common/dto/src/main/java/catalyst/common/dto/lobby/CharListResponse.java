package catalyst.common.dto.lobby;

import catalyst.common.network.GatewayFrame;
import catalyst.common.dto.lobby.LobbyGatewayMessage;
import catalyst.common.network.ResponseCode;
import java.util.List;

public record CharListResponse(
    ResponseCode code,
    List<CharacterDto> characters
) implements LobbyGatewayMessage {

    public record CharacterDto(
        String id,
        String name,
        int race,
        String raceName,
        int size,
        int face,
        int mainJob,
        String jobName,
        int nation,
        int zone
    ) {}
}
