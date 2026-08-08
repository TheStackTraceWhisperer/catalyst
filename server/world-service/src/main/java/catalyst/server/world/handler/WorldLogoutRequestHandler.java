package catalyst.server.world.handler;

import catalyst.common.dto.LogoutRequest;
import catalyst.common.dto.LogoutResponse;
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
public class WorldLogoutRequestHandler implements PacketHandler<LogoutRequest> {

    private final SessionRepository sessions;

    @Override
    public Class<LogoutRequest> getPacketType() {
        return LogoutRequest.class;
    }

    @Override
    public LogoutResponse handle(LogoutRequest req) {
        if (req == null) {
            return new LogoutResponse("-", ResponseCode.CONFLICT, null);
        }

        String sessionId = normalize(req.sessionId());
        if (sessionId.isBlank()) {
            return new LogoutResponse("-", ResponseCode.CONFLICT, null);
        }
        try {
            sessions.delete(sessionId);
            log.info("LOGOUT session={}", sessionId);
            return new LogoutResponse(sessionId, ResponseCode.OK, null);
        } catch (SQLException e) {
            log.error("LOGOUT_ERR session={}", sessionId, e);
            return new LogoutResponse(null, ResponseCode.ERROR, "Failed to close session");
        }
    }

    private String normalize(String v) { return v == null ? "" : v.trim(); }
}
