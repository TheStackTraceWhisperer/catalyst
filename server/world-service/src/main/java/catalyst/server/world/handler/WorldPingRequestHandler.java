package catalyst.server.world.handler;

import catalyst.common.dto.world.PingRequest;
import catalyst.common.dto.world.PingResponse;
import catalyst.server.common.network.PacketHandler;
import catalyst.common.network.ResponseCode;
import catalyst.server.world.repository.SessionRepository;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.sql.SQLException;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class WorldPingRequestHandler implements PacketHandler<PingRequest> {

    private final SessionRepository sessions;

    @Override
    public Class<PingRequest> getPacketType() {
        return PingRequest.class;
    }

    @Override
    public Object handle(PingRequest req, String sessionId) {
        sessionId = normalize(sessionId);
        if (sessionId.isBlank()) {
            return new PingResponse(null, null, ResponseCode.CONFLICT, "Missing sessionId");
        }
        try {
            if (!sessions.ping(sessionId)) {
                return new PingResponse(null, null, ResponseCode.NOT_FOUND, "Session not found");
            }
            return new PingResponse("PONG", sessionId, ResponseCode.OK, null);
        } catch (SQLException e) {
            log.error("PING_ERR session={}", sessionId, e);
            return new PingResponse(null, null, ResponseCode.ERROR, "Failed to update keepalive");
        }
    }

    private String normalize(String v) { return v == null ? "" : v.trim(); }
}
