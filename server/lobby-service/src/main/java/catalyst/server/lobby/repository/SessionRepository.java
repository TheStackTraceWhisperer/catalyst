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
}
