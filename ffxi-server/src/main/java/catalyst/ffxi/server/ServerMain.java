package catalyst.ffxi.server;

import catalyst.ffxi.common.model.CharacterIdentity;
import catalyst.ffxi.common.net.MessageFrame;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ServerMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerMain.class);
    private static final Pattern CHARACTER_NAME_PATTERN = Pattern.compile("^[A-Za-z]{3,15}$");
    private static final long SESSION_TIMEOUT_SECONDS = 30L;
    private static final long AUTH_TICKET_TIMEOUT_SECONDS = 300L;
    private static final int ARGON2_ITERATIONS = 3;
    private static final int ARGON2_MEMORY_KIB = 65_536;
    private static final int ARGON2_PARALLELISM = 1;

    // LSB race encoding: 1=HumeM 2=HumeF 3=ElvaanM 4=ElvaanF 5=TaruM 6=TaruF 7=Mithra 8=Galka
    // minSize/maxSize: Tarutaru forced small (0), Galka forced large (2), others 0-2
    private static final Map<Integer, RaceRule> RACE_RULES = Map.of(
        1, new RaceRule("Hume Male",       0, 2),
        2, new RaceRule("Hume Female",     0, 2),
        3, new RaceRule("Elvaan Male",     0, 2),
        4, new RaceRule("Elvaan Female",   0, 2),
        5, new RaceRule("Tarutaru Male",   0, 0),
        6, new RaceRule("Tarutaru Female", 0, 0),
        7, new RaceRule("Mithra",          0, 2),
        8, new RaceRule("Galka",           2, 2)
    );

    // Zones by nation: 0=San d'Oria, 1=Bastok, 2=Windurst
    private static final List<List<ZoneSpawn>> NATION_ZONES = List.of(
        List.of( // 0 = San d'Oria
            new ZoneSpawn(230, -64.0f, -1.0f,  209.0f, 0),   // Southern San d'Oria
            new ZoneSpawn(231, -32.0f,  0.0f,  -20.0f, 0),   // Northern San d'Oria
            new ZoneSpawn(232,  43.0f,  0.0f,   -9.0f, 0)    // Port San d'Oria
        ),
        List.of( // 1 = Bastok
            new ZoneSpawn(234,  39.0f,  0.0f,   58.0f, 0),   // Bastok Mines
            new ZoneSpawn(235,  27.0f,  0.0f,  -24.0f, 0),   // Bastok Markets
            new ZoneSpawn(233, -29.0f, -1.0f,    5.0f, 0)    // Port Bastok
        ),
        List.of( // 2 = Windurst
            new ZoneSpawn(238, -55.0f,  0.0f,   71.0f, 0),   // Windurst Waters
            new ZoneSpawn(239,  -6.0f,  0.0f,   37.0f, 0),   // Port Windurst
            new ZoneSpawn(240, -95.0f, -1.0f,   40.0f, 0)    // Windurst Woods
        )
    );

    // Starting jobs: WAR=1 MNK=2 WHM=3 BLM=4 RDM=5 THF=6
    private static final int MIN_STARTING_JOB = 1;
    private static final int MAX_STARTING_JOB = 6;

    private final int port;
    private final HikariDataSource dataSource;
    private final Random rng = new Random();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<String, AuthTicket> authTickets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> sessionZones = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, AtomicInteger> zonePopulation = new ConcurrentHashMap<>();

    public ServerMain(int port, String jdbcUrl, String jdbcUser, String jdbcPassword) {
        this.port = port;
        this.dataSource = createDataSource(jdbcUrl, jdbcUser, jdbcPassword);
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("FFXI_SERVER_PORT", "35555"));
        String jdbcUrl = System.getenv().getOrDefault("FFXI_DB_URL", "jdbc:postgresql://localhost:5432/ffxi");
        String jdbcUser = System.getenv().getOrDefault("FFXI_DB_USER", "ffxi");
        String jdbcPassword = System.getenv().getOrDefault("FFXI_DB_PASSWORD", "ffxi");

        ServerMain server = new ServerMain(port, jdbcUrl, jdbcUser, jdbcPassword);
        server.initDatabase();
        server.start();
    }

    private void start() throws Exception {
        scheduler.scheduleAtFixedRate(this::cleanupSessions, 10, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::cleanupAuthTickets, 10, 10, TimeUnit.SECONDS);

        QuicServerTransport quicServer = new QuicServerTransport(port, this::dispatch);
        try {
            quicServer.start();
            LOGGER.info("FFXI server listening on UDP port {} (QUIC)", port);
            quicServer.awaitShutdown();
        } finally {
            quicServer.stop();
            scheduler.shutdownNow();
            dataSource.close();
        }
    }

    MessageFrame dispatch(MessageFrame request) {
        return switch (request.type()) {
            case "LOGIN"       -> handleLogin(request);
            case "CHAR_LIST"   -> handleCharacterList(request);
            case "CHAR_CREATE" -> handleCharacterCreate(request);
            case "CHAR_DELETE" -> handleCharacterDelete(request);
            case "CHAR_SELECT" -> handleCharacterSelect(request);
            case "PLAY"        -> handlePlay(request);
            case "PING"        -> handlePing(request);
            case "LOGOUT"      -> handleLogout(request);
            default            -> error("UNKNOWN_REQUEST", "Unsupported message type: " + request.type());
        };
    }

    private MessageFrame handleLogin(MessageFrame frame) {
        String username = normalize(frame.get("username"));
        String password = normalize(frame.get("password"));
        if (username.isBlank() || password.isBlank()) {
            return loginError("INVALID_CREDENTIALS", "Username and password are required");
        }

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT id, password_hash, status FROM accounts WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    LOGGER.info("LOGIN_ERR user={} reason=account_not_found", username);
                    return loginError("INVALID_CREDENTIALS", "Invalid username or password");
                }
                long accountId = rs.getLong("id");
                String passwordHash = rs.getString("password_hash");
                String status = rs.getString("status");
                if (!"active".equalsIgnoreCase(status)) {
                    LOGGER.info("LOGIN_ERR user={} account={} reason=account_not_active status={}", username, accountId, status);
                    return loginError("ACCOUNT_DISABLED", "Account is not active");
                }
                Argon2 argon2 = Argon2Factory.create();
                if (!argon2.verify(passwordHash, password.toCharArray())) {
                    LOGGER.info("LOGIN_ERR user={} account={} reason=bad_password", username, accountId);
                    return loginError("INVALID_CREDENTIALS", "Invalid username or password");
                }
                String authToken = UUID.randomUUID().toString();
                long expiresAtMs = System.currentTimeMillis() + Duration.ofSeconds(AUTH_TICKET_TIMEOUT_SECONDS).toMillis();
                authTickets.put(authToken, new AuthTicket(accountId, expiresAtMs));
                LOGGER.info("LOGIN_OK user={} account={}", username, accountId);
                return new MessageFrame("LOGIN_OK", Map.of(
                    "code", "OK",
                    "message", "Authenticated",
                    "authToken", authToken,
                    "accountId", Long.toString(accountId)));
            }
        } catch (SQLException e) {
            LOGGER.error("LOGIN_ERR user={} reason=db_error", username, e);
            return loginError("SERVER_ERROR", "Authentication backend unavailable");
        }
    }

    private MessageFrame handleCharacterList(MessageFrame frame) {
        Long accountId = authenticateTicket(frame.get("authToken"));
        if (accountId == null) {
            return error("UNAUTHORIZED", "Invalid or expired auth token");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT id, name, race, size, face, main_job, nation, current_zone_id
                 FROM characters
                 WHERE account_id = ? AND deleted_at IS NULL
                 ORDER BY id
                 """)) {
            statement.setLong(1, accountId);
            try (ResultSet rs = statement.executeQuery()) {
                Map<String, String> fields = new LinkedHashMap<>();
                int index = 0;
                while (rs.next()) {
                    int raceId = rs.getInt("race");
                    fields.put("char" + index + "_id",       Long.toString(rs.getLong("id")));
                    fields.put("char" + index + "_name",     rs.getString("name"));
                    fields.put("char" + index + "_race",     Integer.toString(raceId));
                    fields.put("char" + index + "_raceName", raceNameFor(raceId));
                    fields.put("char" + index + "_size",     Integer.toString(rs.getInt("size")));
                    fields.put("char" + index + "_face",     Integer.toString(rs.getInt("face")));
                    fields.put("char" + index + "_mainJob",  Integer.toString(rs.getInt("main_job")));
                    fields.put("char" + index + "_jobName",  jobNameFor(rs.getInt("main_job")));
                    fields.put("char" + index + "_nation",   Integer.toString(rs.getInt("nation")));
                    fields.put("char" + index + "_zone",     Integer.toString(rs.getInt("current_zone_id")));
                    index++;
                }
                fields.put("count", Integer.toString(index));
                LOGGER.info("CHAR_LIST_OK account={} count={}", accountId, index);
                return new MessageFrame("CHAR_LIST_OK", fields);
            }
        } catch (SQLException e) {
            LOGGER.error("CHAR_LIST_ERR account={}", accountId, e);
            return error("SERVER_ERROR", "Failed to load characters");
        }
    }

    private MessageFrame handleCharacterCreate(MessageFrame frame) {
        Long accountId = authenticateTicket(frame.get("authToken"));
        if (accountId == null) {
            return error("UNAUTHORIZED", "Invalid or expired auth token");
        }

        String name    = normalize(frame.get("name"));
        int race       = parseInt(frame.get("race"), -1);
        int size       = parseInt(frame.get("size"), -1);
        int face       = parseInt(frame.get("face"), -1);
        int mainJob    = parseInt(frame.get("mainJob"), -1);
        int nation     = parseInt(frame.get("nation"), -1);

        if (!CHARACTER_NAME_PATTERN.matcher(name).matches()) {
            return error("INVALID_NAME", "Character name must be 3-15 letters (A-Z)");
        }

        RaceRule raceRule = RACE_RULES.get(race);
        if (raceRule == null) {
            return error("INVALID_RACE", "Race must be 1-8 (see LSB encoding)");
        }
        if (size < raceRule.minSize() || size > raceRule.maxSize()) {
            return error("INVALID_SIZE",
                "Size for " + raceRule.name() + " must be " + raceRule.minSize() + ".." + raceRule.maxSize());
        }
        if (face < 0 || face > 15) {
            return error("INVALID_FACE", "Face must be 0-15");
        }
        // LSB clamps starting job to 1-6
        if (mainJob < MIN_STARTING_JOB || mainJob > MAX_STARTING_JOB) {
            mainJob = Math.clamp(mainJob, MIN_STARTING_JOB, MAX_STARTING_JOB);
        }
        if (nation < 0 || nation > 2) {
            return error("INVALID_NATION", "Nation must be 0 (Sandy), 1 (Bastok), or 2 (Windurst)");
        }

        ZoneSpawn spawn = NATION_ZONES.get(nation).get(rng.nextInt(3));

        try (Connection connection = dataSource.getConnection()) {
            long characterId;
            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO characters (
                  account_id, name, race, size, face, main_job, nation,
                  home_zone_id, home_x, home_y, home_z, home_rot,
                  current_zone_id, current_x, current_y, current_z, current_rot
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """)) {
                statement.setLong(1, accountId);
                statement.setString(2, name);
                statement.setInt(3, race);
                statement.setInt(4, size);
                statement.setInt(5, face);
                statement.setInt(6, mainJob);
                statement.setInt(7, nation);
                statement.setInt(8, spawn.zoneId());
                statement.setFloat(9, spawn.x());
                statement.setFloat(10, spawn.y());
                statement.setFloat(11, spawn.z());
                statement.setFloat(12, spawn.rot());
                statement.setInt(13, spawn.zoneId());
                statement.setFloat(14, spawn.x());
                statement.setFloat(15, spawn.y());
                statement.setFloat(16, spawn.z());
                statement.setFloat(17, spawn.rot());
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return error("SERVER_ERROR", "Failed to create character");
                    }
                    characterId = rs.getLong("id");
                }
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    LOGGER.info("CHAR_CREATE_ERR account={} reason=duplicate_name name={}", accountId, name);
                    return error("NAME_ALREADY_TAKEN", "Character name is already in use");
                }
                throw e;
            }

            insertCharacterJobs(connection, characterId, mainJob);

            LOGGER.info("CHAR_CREATE_OK account={} characterId={} name={} race={} size={} face={} job={} nation={} zone={}",
                accountId, characterId, name, race, size, face, mainJob, nation, spawn.zoneId());
            return new MessageFrame("CHAR_CREATE_OK", Map.of(
                "characterId", Long.toString(characterId),
                "name", name));
        } catch (SQLException e) {
            LOGGER.error("CHAR_CREATE_ERR account={} reason=db_error", accountId, e);
            return error("SERVER_ERROR", "Failed to create character");
        }
    }

    private MessageFrame handleCharacterDelete(MessageFrame frame) {
        Long accountId = authenticateTicket(frame.get("authToken"));
        if (accountId == null) {
            return error("UNAUTHORIZED", "Invalid or expired auth token");
        }
        long characterId = parseLong(frame.get("characterId"), -1L);
        if (characterId <= 0) {
            return error("INVALID_CHARACTER", "characterId is required");
        }
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement check = connection.prepareStatement(
                "SELECT 1 FROM accounts_sessions WHERE character_id = ? LIMIT 1")) {
                check.setLong(1, characterId);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        return error("CHARACTER_ACTIVE", "Character is currently online");
                    }
                }
            }
            try (PreparedStatement softDelete = connection.prepareStatement("""
                UPDATE characters SET deleted_at = NOW()
                WHERE id = ? AND account_id = ? AND deleted_at IS NULL
                """)) {
                softDelete.setLong(1, characterId);
                softDelete.setLong(2, accountId);
                if (softDelete.executeUpdate() == 0) {
                    return error("CHARACTER_NOT_FOUND", "Character not found");
                }
            }
            LOGGER.info("CHAR_DELETE_OK account={} characterId={}", accountId, characterId);
            return new MessageFrame("CHAR_DELETE_OK", Map.of("characterId", Long.toString(characterId)));
        } catch (SQLException e) {
            LOGGER.error("CHAR_DELETE_ERR account={} characterId={}", accountId, characterId, e);
            return error("SERVER_ERROR", "Failed to delete character");
        }
    }

    private MessageFrame handleCharacterSelect(MessageFrame frame) {
        Long accountId = authenticateTicket(frame.get("authToken"));
        if (accountId == null) {
            return error("UNAUTHORIZED", "Invalid or expired auth token");
        }
        long characterId = parseLong(frame.get("characterId"), -1L);
        if (characterId <= 0) {
            return error("INVALID_CHARACTER", "characterId is required");
        }
        try (Connection connection = dataSource.getConnection()) {
            if (hasActiveSession(connection, accountId)) {
                return error("ALREADY_ONLINE", "Account is already online");
            }
            CharacterIdentity identity = loadCharacterIdentity(connection, accountId, characterId);
            if (identity == null) {
                return error("CHARACTER_NOT_FOUND", "Character not found");
            }
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("characterId",  Long.toString(characterId));
            fields.put("characterName", identity.name());
            fields.put("homeZoneId",   Integer.toString(identity.homeZoneId()));
            fields.put("currentZoneId", Integer.toString(identity.currentZoneId()));
            fields.put("x",   Float.toString(identity.currentX()));
            fields.put("y",   Float.toString(identity.currentY()));
            fields.put("z",   Float.toString(identity.currentZ()));
            fields.put("rot", Float.toString(identity.currentHeading()));
            LOGGER.info("CHAR_SELECT_OK account={} characterId={} zone={}", accountId, characterId, identity.currentZoneId());
            return new MessageFrame("CHAR_SELECT_OK", fields);
        } catch (SQLException e) {
            LOGGER.error("CHAR_SELECT_ERR account={} characterId={}", accountId, characterId, e);
            return error("SERVER_ERROR", "Failed to load character");
        }
    }

    private MessageFrame handlePlay(MessageFrame frame) {
        Long accountId = authenticateTicket(frame.get("authToken"));
        if (accountId == null) {
            return error("UNAUTHORIZED", "Invalid or expired auth token");
        }
        long characterId = parseLong(frame.get("characterId"), -1L);
        if (characterId <= 0) {
            return error("INVALID_CHARACTER", "characterId is required");
        }
        try (Connection connection = dataSource.getConnection()) {
            CharacterIdentity identity = loadCharacterIdentity(connection, accountId, characterId);
            if (identity == null) {
                return error("CHARACTER_NOT_FOUND", "Character not found");
            }
            String sessionId = UUID.randomUUID().toString();
            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO accounts_sessions (session_id, account_id, character_id, zone_id, last_seen_at)
                VALUES (?, ?, ?, ?, NOW())
                """)) {
                statement.setObject(1, UUID.fromString(sessionId));
                statement.setLong(2, accountId);
                statement.setLong(3, characterId);
                statement.setInt(4, identity.currentZoneId());
                statement.executeUpdate();
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    LOGGER.info("PLAY_ERR account={} characterId={} reason=already_online", accountId, characterId);
                    return error("ALREADY_ONLINE", "Account or character is already online");
                }
                throw e;
            }
            int playersInZone = joinZone(sessionId, identity.currentZoneId());
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("sessionId",    sessionId);
            fields.put("accountId",    Long.toString(accountId));
            fields.put("characterId",  Long.toString(characterId));
            fields.put("characterName", identity.name());
            fields.put("zoneId",       Integer.toString(identity.currentZoneId()));
            fields.put("playersInZone", Integer.toString(playersInZone));
            fields.put("homeZoneId",   Integer.toString(identity.homeZoneId()));
            fields.put("x",   Float.toString(identity.currentX()));
            fields.put("y",   Float.toString(identity.currentY()));
            fields.put("z",   Float.toString(identity.currentZ()));
            fields.put("rot", Float.toString(identity.currentHeading()));
            LOGGER.info("PLAY_OK account={} characterId={} session={} zone={} playersInZone={}",
                accountId, characterId, sessionId, identity.currentZoneId(), playersInZone);
            return new MessageFrame("PLAY_OK", fields);
        } catch (SQLException e) {
            LOGGER.error("PLAY_ERR account={} characterId={}", accountId, characterId, e);
            return error("SERVER_ERROR", "Failed to start session");
        }
    }

    private MessageFrame handlePing(MessageFrame frame) {
        String sessionId = normalize(frame.get("sessionId"));
        if (sessionId.isBlank()) {
            return error("SESSION_NOT_FOUND", "Missing sessionId");
        }
        UUID sessionUuid = parseUuid(sessionId);
        if (sessionUuid == null) {
            return error("SESSION_NOT_FOUND", "Session not found");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE accounts_sessions SET last_seen_at = NOW() WHERE session_id = ?")) {
            statement.setObject(1, sessionUuid);
            if (statement.executeUpdate() == 0) {
                return error("SESSION_NOT_FOUND", "Session not found");
            }
            return new MessageFrame("PONG", Map.of("sessionId", sessionId));
        } catch (SQLException e) {
            LOGGER.error("PING_ERR session={}", sessionId, e);
            return error("SERVER_ERROR", "Failed to update keepalive");
        }
    }

    private MessageFrame handleLogout(MessageFrame frame) {
        String sessionId = normalize(frame.get("sessionId"));
        if (sessionId.isBlank()) {
            return new MessageFrame("BYE", Map.of("sessionId", "-"));
        }
        UUID sessionUuid = parseUuid(sessionId);
        if (sessionUuid == null) {
            return new MessageFrame("BYE", Map.of("sessionId", sessionId));
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM accounts_sessions WHERE session_id = ? RETURNING zone_id")) {
            statement.setObject(1, sessionUuid);
            Integer zoneId = null;
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    zoneId = rs.getInt("zone_id");
                }
            }
            leaveZone(sessionId, zoneId);
            LOGGER.info("LOGOUT session={}", sessionId);
            return new MessageFrame("BYE", Map.of("sessionId", sessionId));
        } catch (SQLException e) {
            LOGGER.error("LOGOUT_ERR session={}", sessionId, e);
            return error("SERVER_ERROR", "Failed to close session");
        }
    }

    private void cleanupSessions() {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement selectStale = connection.prepareStatement("""
                SELECT session_id, zone_id FROM accounts_sessions
                WHERE last_seen_at < NOW() - (? * INTERVAL '1 second')
                """)) {
                selectStale.setLong(1, SESSION_TIMEOUT_SECONDS);
                try (ResultSet rs = selectStale.executeQuery()) {
                    while (rs.next()) {
                        leaveZone(rs.getString("session_id"), rs.getInt("zone_id"));
                    }
                }
            }
            try (PreparedStatement deleteStale = connection.prepareStatement("""
                DELETE FROM accounts_sessions
                WHERE last_seen_at < NOW() - (? * INTERVAL '1 second')
                """)) {
                deleteStale.setLong(1, SESSION_TIMEOUT_SECONDS);
                int removed = deleteStale.executeUpdate();
                if (removed > 0) {
                    LOGGER.info("SESSION_CLEANUP removed={}", removed);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("SESSION_CLEANUP_ERR", e);
        }
    }

    private void cleanupAuthTickets() {
        long now = System.currentTimeMillis();
        int removed = 0;
        for (Map.Entry<String, AuthTicket> entry : authTickets.entrySet()) {
            if (entry.getValue().expiresAtMs() <= now && authTickets.remove(entry.getKey(), entry.getValue())) {
                removed++;
            }
        }
        if (removed > 0) {
            LOGGER.info("AUTH_TICKET_CLEANUP removed={}", removed);
        }
    }

    private Long authenticateTicket(String authToken) {
        String token = normalize(authToken);
        if (token.isBlank()) {
            return null;
        }
        AuthTicket ticket = authTickets.get(token);
        if (ticket == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (ticket.expiresAtMs() <= now) {
            authTickets.remove(token, ticket);
            return null;
        }
        authTickets.put(token, new AuthTicket(ticket.accountId(), now + Duration.ofSeconds(AUTH_TICKET_TIMEOUT_SECONDS).toMillis()));
        return ticket.accountId();
    }

    private CharacterIdentity loadCharacterIdentity(Connection connection, long accountId, long characterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT id, name, home_zone_id, home_x, home_y, home_z, home_rot,
                   current_zone_id, current_x, current_y, current_z, current_rot
            FROM characters
            WHERE id = ? AND account_id = ? AND deleted_at IS NULL
            """)) {
            statement.setLong(1, characterId);
            statement.setLong(2, accountId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new CharacterIdentity(
                    Long.toString(rs.getLong("id")),
                    rs.getString("name"),
                    rs.getInt("home_zone_id"),
                    rs.getFloat("home_x"),
                    rs.getFloat("home_y"),
                    rs.getFloat("home_z"),
                    rs.getFloat("home_rot"),
                    rs.getInt("current_zone_id"),
                    rs.getFloat("current_x"),
                    rs.getFloat("current_y"),
                    rs.getFloat("current_z"),
                    rs.getFloat("current_rot")
                );
            }
        }
    }

    private boolean hasActiveSession(Connection connection, long accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT 1 FROM accounts_sessions WHERE account_id = ? LIMIT 1")) {
            statement.setLong(1, accountId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void insertCharacterJobs(Connection connection, long characterId, int mainJob) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO character_jobs (character_id, war, mnk, whm, blm, rdm, thf)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """)) {
            statement.setLong(1, characterId);
            for (int jobSlot = 1; jobSlot <= 6; jobSlot++) {
                statement.setInt(jobSlot + 1, (jobSlot == mainJob) ? 1 : 0);
            }
            statement.executeUpdate();
        }
    }

    private void initDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                CREATE TABLE IF NOT EXISTS accounts (
                  id BIGSERIAL PRIMARY KEY,
                  username VARCHAR(64) NOT NULL UNIQUE,
                  password_hash TEXT NOT NULL,
                  status VARCHAR(16) NOT NULL DEFAULT 'active',
                  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                );
                CREATE TABLE IF NOT EXISTS accounts_sessions (
                  session_id UUID PRIMARY KEY,
                  account_id BIGINT NOT NULL REFERENCES accounts(id),
                  character_id BIGINT NOT NULL,
                  zone_id INT NOT NULL DEFAULT 0,
                  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                  UNIQUE (account_id),
                  UNIQUE (character_id)
                );
                CREATE INDEX IF NOT EXISTS idx_accounts_sessions_last_seen
                  ON accounts_sessions(last_seen_at);
                CREATE TABLE IF NOT EXISTS characters (
                  id BIGSERIAL PRIMARY KEY,
                  account_id BIGINT NOT NULL REFERENCES accounts(id),
                  name VARCHAR(16) NOT NULL,
                  race SMALLINT NOT NULL,
                  size SMALLINT NOT NULL DEFAULT 1,
                  face SMALLINT NOT NULL DEFAULT 0,
                  main_job SMALLINT NOT NULL DEFAULT 1,
                  nation SMALLINT NOT NULL DEFAULT 0,
                  home_zone_id INT NOT NULL,
                  home_x REAL NOT NULL,
                  home_y REAL NOT NULL,
                  home_z REAL NOT NULL,
                  home_rot REAL NOT NULL,
                  current_zone_id INT NOT NULL,
                  current_x REAL NOT NULL,
                  current_y REAL NOT NULL,
                  current_z REAL NOT NULL,
                  current_rot REAL NOT NULL,
                  deleted_at TIMESTAMPTZ NULL,
                  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                );
                ALTER TABLE characters ADD COLUMN IF NOT EXISTS size SMALLINT NOT NULL DEFAULT 1;
                ALTER TABLE characters ADD COLUMN IF NOT EXISTS main_job SMALLINT NOT NULL DEFAULT 1;
                ALTER TABLE characters ADD COLUMN IF NOT EXISTS nation SMALLINT NOT NULL DEFAULT 0;
                CREATE UNIQUE INDEX IF NOT EXISTS idx_characters_name_active
                  ON characters (LOWER(name)) WHERE deleted_at IS NULL;
                CREATE INDEX IF NOT EXISTS idx_characters_account_active
                  ON characters (account_id) WHERE deleted_at IS NULL;
                CREATE TABLE IF NOT EXISTS character_jobs (
                  character_id BIGINT PRIMARY KEY REFERENCES characters(id) ON DELETE CASCADE,
                  war SMALLINT NOT NULL DEFAULT 0,
                  mnk SMALLINT NOT NULL DEFAULT 0,
                  whm SMALLINT NOT NULL DEFAULT 0,
                  blm SMALLINT NOT NULL DEFAULT 0,
                  rdm SMALLINT NOT NULL DEFAULT 0,
                  thf SMALLINT NOT NULL DEFAULT 0,
                  pld SMALLINT NOT NULL DEFAULT 0,
                  drk SMALLINT NOT NULL DEFAULT 0,
                  bst SMALLINT NOT NULL DEFAULT 0,
                  brd SMALLINT NOT NULL DEFAULT 0,
                  rng SMALLINT NOT NULL DEFAULT 0,
                  sam SMALLINT NOT NULL DEFAULT 0,
                  nin SMALLINT NOT NULL DEFAULT 0,
                  drg SMALLINT NOT NULL DEFAULT 0,
                  smn SMALLINT NOT NULL DEFAULT 0,
                  blu SMALLINT NOT NULL DEFAULT 0,
                  cor SMALLINT NOT NULL DEFAULT 0,
                  pup SMALLINT NOT NULL DEFAULT 0,
                  dnc SMALLINT NOT NULL DEFAULT 0,
                  sch SMALLINT NOT NULL DEFAULT 0,
                  geo SMALLINT NOT NULL DEFAULT 0,
                  run SMALLINT NOT NULL DEFAULT 0
                );
                """)) {
                statement.execute();
            }
            bootstrapDevAccount(connection);
        }
    }

    private void bootstrapDevAccount(Connection connection) throws SQLException {
        try (PreparedStatement check = connection.prepareStatement("SELECT 1 FROM accounts WHERE username = ?")) {
            check.setString(1, "dev");
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    LOGGER.info("Bootstrap account 'dev' already present");
                    return;
                }
            }
        }
        Argon2 argon2 = Argon2Factory.create();
        String hash = argon2.hash(ARGON2_ITERATIONS, ARGON2_MEMORY_KIB, ARGON2_PARALLELISM, "dev".toCharArray());
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO accounts (username, password_hash, status) VALUES (?, ?, ?)")) {
            insert.setString(1, "dev");
            insert.setString(2, hash);
            insert.setString(3, "active");
            insert.executeUpdate();
            LOGGER.info("Bootstrapped account username=dev password=dev");
        }
    }

    private static HikariDataSource createDataSource(String jdbcUrl, String jdbcUser, String jdbcPassword) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(jdbcUser);
        config.setPassword(jdbcPassword);
        config.setMaximumPoolSize(8);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);
        config.setPoolName("ffxi-server-hikari");
        return new HikariDataSource(config);
    }

    private int joinZone(String sessionId, int zoneId) {
        sessionZones.put(sessionId, zoneId);
        int count = zonePopulation.computeIfAbsent(zoneId, ignored -> new AtomicInteger(0)).incrementAndGet();
        LOGGER.info("ZONE_ENTER zone={} session={} playersInZone={}", zoneId, sessionId, count);
        return count;
    }

    private void leaveZone(String sessionId, Integer zoneIdHint) {
        Integer zoneId = sessionZones.remove(sessionId);
        if (zoneId == null) {
            zoneId = zoneIdHint;
        }
        if (zoneId == null) {
            return;
        }
        AtomicInteger count = zonePopulation.get(zoneId);
        if (count == null) {
            return;
        }
        int remaining = Math.max(0, count.decrementAndGet());
        if (remaining == 0) {
            zonePopulation.remove(zoneId, count);
        }
        LOGGER.info("ZONE_LEAVE zone={} session={} playersInZone={}", zoneId, sessionId, remaining);
    }

    private MessageFrame loginError(String code, String message) {
        return new MessageFrame("LOGIN_ERR", Map.of("code", code, "message", message));
    }

    private MessageFrame error(String code, String message) {
        return new MessageFrame("ERROR", Map.of("code", code, "message", message));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return fallback; }
    }

    private long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return Long.parseLong(value); } catch (NumberFormatException e) { return fallback; }
    }

    private UUID parseUuid(String value) {
        try { return UUID.fromString(value); } catch (IllegalArgumentException e) { return null; }
    }

    private String raceNameFor(int raceId) {
        RaceRule rule = RACE_RULES.get(raceId);
        return rule != null ? rule.name() : "Unknown";
    }

    private String jobNameFor(int jobId) {
        return switch (jobId) {
            case 1 -> "WAR"; case 2 -> "MNK"; case 3 -> "WHM";
            case 4 -> "BLM"; case 5 -> "RDM"; case 6 -> "THF";
            default -> "???";
        };
    }

    private record AuthTicket(long accountId, long expiresAtMs) {}
    private record RaceRule(String name, int minSize, int maxSize) {}
    private record ZoneSpawn(int zoneId, float x, float y, float z, float rot) {}
}
