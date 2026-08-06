package catalyst.server.lobby.repository;

import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Singleton
@RequiredArgsConstructor
public class SessionRepository {

    private final DataSource dataSource;

    public boolean hasActiveSession(long accountId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement s = c.prepareStatement(
                 "SELECT 1 FROM accounts_sessions WHERE account_id = ? LIMIT 1")) {
            s.setLong(1, accountId);
            try (ResultSet rs = s.executeQuery()) { return rs.next(); }
        }
    }

    public boolean characterHasActiveSession(long characterId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement s = c.prepareStatement(
                 "SELECT 1 FROM accounts_sessions WHERE character_id = ? LIMIT 1")) {
            s.setLong(1, characterId);
            try (ResultSet rs = s.executeQuery()) { return rs.next(); }
        }
    }

    public String create(long accountId, long characterId, int zoneId) throws SQLException {
        String sessionId = java.util.UUID.randomUUID().toString();
        try (Connection c = dataSource.getConnection();
             PreparedStatement s = c.prepareStatement("""
                INSERT INTO accounts_sessions (session_id, account_id, character_id, zone_id, last_seen_at)
                VALUES (?, ?, ?, ?, NOW())
                """)) {
            s.setObject(1, java.util.UUID.fromString(sessionId));
            s.setLong(2, accountId);
            s.setLong(3, characterId);
            s.setInt(4, zoneId);
            s.executeUpdate();
        }
        return sessionId;
    }

    public int getZonePopulation(int zoneId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement s = c.prepareStatement(
                 "SELECT COUNT(*) FROM accounts_sessions WHERE zone_id = ?")) {
            s.setInt(1, zoneId);
            try (ResultSet rs = s.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
                return 0;
            }
        }
    }
}
