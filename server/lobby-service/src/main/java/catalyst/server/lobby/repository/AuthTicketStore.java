package catalyst.server.lobby.repository;

import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.UUID;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class AuthTicketStore {
    private final DataSource dataSource;

    public String issue(long accountId) {
        String token = UUID.randomUUID().toString();
        long expiresAt = System.currentTimeMillis() + 1800000; // 30 mins
        try (Connection c = dataSource.getConnection();
             PreparedStatement s = c.prepareStatement(
                 "INSERT INTO accounts_auth_tokens (token, account_id, expires_at) VALUES (?, ?, ?)")) {
            s.setObject(1, UUID.fromString(token));
            s.setLong(2, accountId);
            s.setTimestamp(3, new Timestamp(expiresAt));
            s.executeUpdate();
        } catch (Exception e) {
            log.error("Failed to issue token", e);
            throw new RuntimeException(e);
        }
        return token;
    }

    public Long validate(String token) {
        if (token == null || token.isBlank()) return null;
        try (Connection c = dataSource.getConnection()) {
            UUID uuid;
            try { uuid = UUID.fromString(token); } catch (IllegalArgumentException e) { return null; }
            
            long accountId = -1;
            try (PreparedStatement s = c.prepareStatement(
                "SELECT account_id FROM accounts_auth_tokens WHERE token = ? AND expires_at > NOW()")) {
                s.setObject(1, uuid);
                try (ResultSet rs = s.executeQuery()) {
                    if (!rs.next()) return null;
                    accountId = rs.getLong("account_id");
                }
            }
            
            // Rolling update: extend expiration
            long newExpiry = System.currentTimeMillis() + 1800000; // 30 mins
            try (PreparedStatement s = c.prepareStatement(
                "UPDATE accounts_auth_tokens SET expires_at = ? WHERE token = ?")) {
                s.setTimestamp(1, new Timestamp(newExpiry));
                s.setObject(2, uuid);
                s.executeUpdate();
            }
            return accountId;
        } catch (Exception e) {
            log.error("Failed to validate token", e);
            return null;
        }
    }

    public void expire(String token) {
        if (token == null || token.isBlank()) return;
        try {
            UUID uuid = UUID.fromString(token);
            try (Connection c = dataSource.getConnection();
                 PreparedStatement s = c.prepareStatement("DELETE FROM accounts_auth_tokens WHERE token = ?")) {
                s.setObject(1, uuid);
                s.executeUpdate();
            }
        } catch (Exception e) {
            log.error("Failed to expire token", e);
        }
    }

    public int removeExpired() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement s = c.prepareStatement("DELETE FROM accounts_auth_tokens WHERE expires_at <= NOW()")) {
            int removed = s.executeUpdate();
            if (removed > 0) log.info("AUTH_TICKET_CLEANUP removed={}", removed);
            return removed;
        } catch (Exception e) {
            log.error("Failed to cleanup expired tokens", e);
            return 0;
        }
    }
}
