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
    public boolean isImmediate() {
        return true;
    }

    @Override
    public Object handle(PingRequest req, String sessionId) {
        sessionId = normalize(sessionId);
        if (sessionId.isBlank()) {
            return new PingResponse(null, null, ResponseCode.CONFLICT, "Missing sessionId");
        }
        
        final String targetSessionId = sessionId;
        Thread.startVirtualThread(() -> {
            try {
                sessions.ping(targetSessionId);
            } catch (SQLException e) {
                log.error("Async keepalive database update failed for session {}", targetSessionId, e);
            }
        });

        return new PingResponse("PONG", sessionId, ResponseCode.OK, null);
    }

    private String normalize(String v) { return v == null ? "" : v.trim(); }
}
