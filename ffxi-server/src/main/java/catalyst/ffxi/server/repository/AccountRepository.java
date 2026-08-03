package catalyst.ffxi.server.repository;

import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class AccountRepository {

    private final DataSource dataSource;

    public record AccountRow(long id, String passwordHash, String status) {}

    public Optional<AccountRow> findByUsername(String username) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement s = c.prepareStatement(
                 "SELECT id, password_hash, status FROM accounts WHERE username = ?")) {
            s.setString(1, username);
            try (ResultSet rs = s.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new AccountRow(rs.getLong("id"), rs.getString("password_hash"), rs.getString("status")));
            }
        }
    }

    public boolean existsByUsername(String username) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement s = c.prepareStatement("SELECT 1 FROM accounts WHERE username = ?")) {
            s.setString(1, username);
            try (ResultSet rs = s.executeQuery()) { return rs.next(); }
        }
    }

    public void insert(String username, String passwordHash, String status) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement s = c.prepareStatement(
                 "INSERT INTO accounts (username, password_hash, status) VALUES (?, ?, ?)")) {
            s.setString(1, username);
            s.setString(2, passwordHash);
            s.setString(3, status);
            s.executeUpdate();
        }
    }
}
