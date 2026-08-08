package catalyst.server.lobby.repository;

import catalyst.common.dto.world.CharacterIdentity;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CharacterRepository {

    private final DataSource dataSource;

    public record CharacterListRow(long id, String name, int race, String raceName, int size, int face,
                                    int mainJob, String jobName, int nation, int currentZoneId) {}

    public List<CharacterListRow> findActiveByAccount(long accountId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement s = c.prepareStatement("""
                SELECT id, name, race, size, face, main_job, nation, current_zone_id
                FROM characters WHERE account_id = ? AND deleted_at IS NULL ORDER BY id
                """)) {
            s.setLong(1, accountId);
            List<CharacterListRow> rows = new ArrayList<>();
            try (ResultSet rs = s.executeQuery()) {
                while (rs.next()) {
                    int raceId = rs.getInt("race");
                    int jobId  = rs.getInt("main_job");
                    rows.add(new CharacterListRow(rs.getLong("id"), rs.getString("name"),
                        raceId, raceNameFor(raceId), rs.getInt("size"), rs.getInt("face"),
                        jobId, jobNameFor(jobId), rs.getInt("nation"), rs.getInt("current_zone_id")));
                }
            }
            return rows;
        }
    }

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

    public long createWithJobs(long accountId, String name, int race, int size, int face, int mainJob, int nation,
                               int zoneId, float x, float y, float z, float rot) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                long characterId = insertCharacter(c, accountId, name, race, size, face, mainJob, nation, zoneId, x, y, z, rot);
                insertJobs(c, characterId, mainJob);
                c.commit();
                return characterId;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private long insertCharacter(Connection c, long accountId, String name, int race, int size, int face, int mainJob, int nation,
                                 int zoneId, float x, float y, float z, float rot) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
            INSERT INTO characters (
              account_id, name, race, size, face, main_job, nation,
              home_zone_id, home_x, home_y, home_z, home_rot,
              current_zone_id, current_x, current_y, current_z, current_rot
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id
            """)) {
            s.setLong(1, accountId); s.setString(2, name); s.setInt(3, race); s.setInt(4, size);
            s.setInt(5, face); s.setInt(6, mainJob); s.setInt(7, nation);
            s.setInt(8, zoneId); s.setFloat(9, x); s.setFloat(10, y); s.setFloat(11, z); s.setFloat(12, rot);
            s.setInt(13, zoneId); s.setFloat(14, x); s.setFloat(15, y); s.setFloat(16, z); s.setFloat(17, rot);
            try (ResultSet rs = s.executeQuery()) {
                rs.next();
                return rs.getLong("id");
            }
        }
    }

    private void insertJobs(Connection c, long characterId, int mainJob) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
            "INSERT INTO character_jobs (character_id,war,mnk,whm,blm,rdm,thf) VALUES (?,?,?,?,?,?,?)")) {
            s.setLong(1, characterId);
            for (int j = 1; j <= 6; j++) s.setInt(j + 1, j == mainJob ? 1 : 0);
            s.executeUpdate();
        }
    }

    public boolean softDelete(long characterId, long accountId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement s = c.prepareStatement("""
                UPDATE characters SET deleted_at = NOW()
                WHERE id = ? AND account_id = ? AND deleted_at IS NULL
                """)) {
            s.setLong(1, characterId); s.setLong(2, accountId);
            return s.executeUpdate() > 0;
        }
    }

    public static String raceNameFor(int raceId) {
        return switch (raceId) {
            case 1 -> "Hume Male"; case 2 -> "Hume Female";
            case 3 -> "Elvaan Male"; case 4 -> "Elvaan Female";
            case 5 -> "Tarutaru Male"; case 6 -> "Tarutaru Female";
            case 7 -> "Mithra"; case 8 -> "Galka";
            default -> "Unknown";
        };
    }

    public static String jobNameFor(int jobId) {
        return switch (jobId) {
            case 1 -> "WAR"; case 2 -> "MNK"; case 3 -> "WHM";
            case 4 -> "BLM"; case 5 -> "RDM"; case 6 -> "THF";
            default -> "???";
        };
    }
}
