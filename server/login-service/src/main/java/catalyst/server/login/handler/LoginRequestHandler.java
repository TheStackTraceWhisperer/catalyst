package catalyst.server.login.handler;

import catalyst.common.network.ResponseCode;
import catalyst.server.common.network.PacketHandler;
import catalyst.common.dto.LoginRequest;
import catalyst.common.dto.LoginResponse;
import catalyst.server.login.properties.ServerProperties;
import catalyst.server.login.repository.AccountRepository;
import catalyst.server.common.repository.AuthTicketStore;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.sql.SQLException;

import catalyst.common.network.GatewayControlMessage;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class LoginRequestHandler implements PacketHandler<LoginRequest> {

    private static final Argon2 ARGON2 = Argon2Factory.create();

    private final AccountRepository accounts;
    private final AuthTicketStore tickets;
    private final ServerProperties props;

    @Override
    public Class<LoginRequest> getPacketType() {
        return LoginRequest.class;
    }

    @Override
    public Object handle(LoginRequest req) {
        String username = normalize(req.username());
        String password = normalize(req.password());
        if (username.isBlank() || password.isBlank()) {
            return error(ResponseCode.UNAUTHORIZED, "Username and password are required");
        }
        try {
            var row = accounts.findByUsername(username);
            if (row.isEmpty()) {
                log.info("LOGIN_ERR user={} reason=not_found", username);
                return error(ResponseCode.UNAUTHORIZED, "Invalid username or password");
            }
            var account = row.get();
            if (!"active".equalsIgnoreCase(account.status())) {
                log.info("LOGIN_ERR user={} account={} reason=not_active", username, account.id());
                return error(ResponseCode.UNAUTHORIZED, "Account is not active");
            }
            if (!ARGON2.verify(account.passwordHash(), password.toCharArray())) {
                log.info("LOGIN_ERR user={} account={} reason=bad_password", username, account.id());
                return error(ResponseCode.UNAUTHORIZED, "Invalid username or password");
            }
            String token = tickets.issue(account.id());
            log.info("LOGIN_OK user={} account={}", username, account.id());

            return new Object[] {
                new GatewayControlMessage("auth_success", token, null),
                new LoginResponse(ResponseCode.OK, "Authenticated", token, account.id())
            };
        } catch (SQLException e) {
            log.error("LOGIN_ERR user={} reason=db_error", username, e);
            return error(ResponseCode.ERROR, "Authentication backend unavailable");
        }
    }

    public void bootstrapDevAccount() {
        try {
            if (accounts.existsByUsername("dev")) {
                log.info("Bootstrap account 'dev' already present");
                return;
            }
            String hash = ARGON2.hash(props.getArgon2Iterations(), props.getArgon2MemoryKib(),
                props.getArgon2Parallelism(), "dev".toCharArray());
            try {
                accounts.insert("dev", hash, "active");
                log.info("Bootstrapped dev account");
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    log.info("Bootstrap account 'dev' already present (concurrent insert)");
                } else {
                    throw e;
                }
            }
        } catch (SQLException e) {
            log.error("Bootstrap dev account failed", e);
        }
    }

    private String normalize(String v) { return v == null ? "" : v.trim(); }

    private LoginResponse error(ResponseCode code, String message) {
        return new LoginResponse(code, message, null, -1);
    }

    public void cleanupTickets() { tickets.removeExpired(); }
}
