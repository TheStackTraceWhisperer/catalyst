package catalyst.server.world.repository;

import catalyst.common.network.model.CharacterIdentity;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Singleton
@RequiredArgsConstructor
public class CharacterRepository {

    private final DataSource dataSource;

    public Optional<CharacterIdentity> findActiveByIdAndAccount(long characterId, long accountId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement s = c.prepareStatement("""
                SELECT id, name, home_zone_id, home_x, home_y, home_z, home_rot,
                       current_zone_id, current_x, current_y, current_z, current_rot
                FROM characters WHERE id = ? AND account_id = ? AND deleted_at IS NULL
                """)) {
            s.setLong(1, characterId);
            s.setLong(2, accountId);
            try (ResultSet rs = s.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new CharacterIdentity(
                    Long.toString(rs.getLong("id")), rs.getString("name"),
                    rs.getInt("home_zone_id"), rs.getFloat("home_x"), rs.getFloat("home_y"),
                    rs.getFloat("home_z"), rs.getFloat("home_rot"),
                    rs.getInt("current_zone_id"), rs.getFloat("current_x"), rs.getFloat("current_y"),
                    rs.getFloat("current_z"), rs.getFloat("current_rot")));
            }
        }
    }
}
