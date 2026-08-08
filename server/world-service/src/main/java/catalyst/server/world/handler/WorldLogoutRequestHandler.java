package catalyst.server.world.handler;

import catalyst.common.dto.LogoutRequest;
import catalyst.common.dto.LogoutResponse;
import catalyst.common.network.GatewayControlMessage;
import catalyst.server.common.network.PacketHandler;
import catalyst.common.network.ResponseCode;
import catalyst.server.common.network.SessionContext;
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
    public Object handle(LogoutRequest req) {
        String sessionId = normalize(SessionContext.getSessionId());
        if (sessionId.isBlank()) {
            return new LogoutResponse("-", ResponseCode.CONFLICT, "Missing sessionId");
        }
        try {
            sessions.delete(sessionId);
            log.info("LOGOUT session={}", sessionId);
            return new Object[] {
                new GatewayControlMessage("logout_success", sessionId, null),
                new LogoutResponse(sessionId, ResponseCode.OK, null)
            };
        } catch (SQLException e) {
            log.error("LOGOUT_ERR session={}", sessionId, e);
            return new LogoutResponse(null, ResponseCode.ERROR, "Failed to close session");
        }
    }

    private String normalize(String v) { return v == null ? "" : v.trim(); }
}
