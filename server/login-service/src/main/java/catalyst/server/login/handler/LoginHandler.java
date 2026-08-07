package catalyst.server.login.handler;

import catalyst.common.network.MessageFrame;
import catalyst.common.network.ResponseCode;
import catalyst.common.dto.LoginRequest;
import catalyst.common.dto.LoginResponse;
import catalyst.common.dto.ProtocolMapper;
import catalyst.server.login.properties.ServerProperties;
import catalyst.server.login.repository.AccountRepository;
import catalyst.server.common.repository.AuthTicketStore;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class LoginHandler {

    private final AccountRepository accounts;
    private final AuthTicketStore tickets;
    private final ServerProperties props;

    public MessageFrame handle(MessageFrame reqFrame) {
        LoginRequest req;
        try {
            req = ProtocolMapper.toLoginRequest(reqFrame);
        } catch (IllegalArgumentException e) {
            return error("LOGIN_ERR", "INVALID_CREDENTIALS", e.getMessage());
        }

        String username = normalize(req.getUsername());
        String password = normalize(req.getPassword());
        if (username.isBlank() || password.isBlank()) {
            return error("LOGIN_ERR", "INVALID_CREDENTIALS", "Username and password are required");
        }
        try {
            var row = accounts.findByUsername(username);
            if (row.isEmpty()) {
                log.info("LOGIN_ERR user={} reason=not_found", username);
                return error("LOGIN_ERR", "INVALID_CREDENTIALS", "Invalid username or password");
            }
            var account = row.get();
            if (!"active".equalsIgnoreCase(account.status())) {
                log.info("LOGIN_ERR user={} account={} reason=not_active", username, account.id());
                return error("LOGIN_ERR", "ACCOUNT_DISABLED", "Account is not active");
            }
            Argon2 argon2 = Argon2Factory.create();
            if (!argon2.verify(account.passwordHash(), password.toCharArray())) {
                log.info("LOGIN_ERR user={} account={} reason=bad_password", username, account.id());
                return error("LOGIN_ERR", "INVALID_CREDENTIALS", "Invalid username or password");
            }
            String token = tickets.issue(account.id());
            log.info("LOGIN_OK user={} account={}", username, account.id());
            
            LoginResponse resp = LoginResponse.builder()
                .code(ResponseCode.OK)
                .message("Authenticated")
                .authToken(token)
                .accountId(account.id())
                .build();
            return ProtocolMapper.fromLoginResponse(resp);
        } catch (SQLException e) {
            log.error("LOGIN_ERR user={} reason=db_error", username, e);
            LoginResponse resp = LoginResponse.builder()
                .code(ResponseCode.ERROR)
                .message("Authentication backend unavailable")
                .build();
            return ProtocolMapper.fromLoginResponse(resp);
        }
    }

    public void bootstrapDevAccount() {
        try {
            if (accounts.existsByUsername("dev")) {
                log.info("Bootstrap account 'dev' already present");
                return;
            }
            Argon2 argon2 = Argon2Factory.create();
            String hash = argon2.hash(props.getArgon2Iterations(), props.getArgon2MemoryKib(),
                props.getArgon2Parallelism(), "dev".toCharArray());
            accounts.insert("dev", hash, "active");
            log.info("Bootstrapped dev account");
        } catch (SQLException e) {
            log.error("Bootstrap dev account failed", e);
        }
    }

    private String normalize(String v) { return v == null ? "" : v.trim(); }

    private MessageFrame error(String type, String code, String message) {
        return MessageFrame.builder(type).put("code", code).put("message", message).build();
    }

    public void cleanupTickets() { tickets.removeExpired(); }
}
