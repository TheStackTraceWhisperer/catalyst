package catalyst.ffxi.server.repository;

import catalyst.ffxi.server.session.ZoneManager;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class SessionRepository {

    private final DataSource dataSource;
    private final ZoneManager zoneManager;

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

    /** Creates the session row and joins the zone. Returns the new sessionId UUID string. */
    public String create(long accountId, long characterId, int zoneId) throws SQLException {
        String sessionId = UUID.randomUUID().toString();
        try (Connection c = dataSource.getConnection();
             PreparedStatement s = c.prepareStatement("""
                INSERT INTO accounts_sessions (session_id, account_id, character_id, zone_id, last_seen_at)
                VALUES (?, ?, ?, ?, NOW())
                """)) {
            s.setObject(1, UUID.fromString(sessionId));
            s.setLong(2, accountId);
            s.setLong(3, characterId);
            s.setInt(4, zoneId);
            s.executeUpdate();
        }
        zoneManager.join(sessionId, zoneId);
        return sessionId;
    }

    public boolean ping(String sessionId) throws SQLException {
        UUID uuid = parseUuid(sessionId);
        if (uuid == null) return false;
        try (Connection c = dataSource.getConnection();
             PreparedStatement s = c.prepareStatement(
                 "UPDATE accounts_sessions SET last_seen_at = NOW() WHERE session_id = ?")) {
            s.setObject(1, uuid);
            return s.executeUpdate() > 0;
        }
    }

    public boolean delete(String sessionId) throws SQLException {
        UUID uuid = parseUuid(sessionId);
        if (uuid == null) return false;
        try (Connection c = dataSource.getConnection();
             PreparedStatement s = c.prepareStatement(
                 "DELETE FROM accounts_sessions WHERE session_id = ? RETURNING zone_id")) {
            s.setObject(1, uuid);
            try (ResultSet rs = s.executeQuery()) {
                if (!rs.next()) return false;
            }
        }
        zoneManager.leave(sessionId);
        return true;
    }

    public int deleteStale(long timeoutSeconds) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            // Collect stale sessions for zone cleanup
            try (PreparedStatement sel = c.prepareStatement(
                "SELECT session_id FROM accounts_sessions WHERE last_seen_at < NOW() - (? * INTERVAL '1 second')")) {
                sel.setLong(1, timeoutSeconds);
                try (ResultSet rs = sel.executeQuery()) {
                    while (rs.next()) zoneManager.leave(rs.getString("session_id"));
                }
            }
            try (PreparedStatement del = c.prepareStatement(
                "DELETE FROM accounts_sessions WHERE last_seen_at < NOW() - (? * INTERVAL '1 second')")) {
                del.setLong(1, timeoutSeconds);
                return del.executeUpdate();
            }
        }
    }

    private static UUID parseUuid(String s) {
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    public int getZonePopulation(int zoneId) {
        return zoneManager.getPopulation(zoneId);
    }
}
