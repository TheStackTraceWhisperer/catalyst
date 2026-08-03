package catalyst.ffxi.server;

import catalyst.ffxi.common.model.CharacterIdentity;
import catalyst.ffxi.common.net.MessageFrame;
import catalyst.ffxi.common.net.WireCodec;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
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

    private static final Map<String, CharacterStartLocation> STARTING_CITIES = Map.of(
        "BASTOK", new CharacterStartLocation(234, -39.0f, 0.0f, -58.0f, 0.0f),
        "SANDORIA", new CharacterStartLocation(230, 10.0f, 0.0f, -75.0f, 0.0f),
        "WINDURST", new CharacterStartLocation(238, -55.0f, 0.0f, 71.0f, 0.0f)
    );
    private static final Map<String, RaceRule> RACE_RULES = Map.of(
        "HUME", new RaceRule((short) 1, true, true),
        "ELVAAN", new RaceRule((short) 2, true, true),
        "TARUTARU", new RaceRule((short) 3, true, true),
        "MITHRA", new RaceRule((short) 4, false, true),
        "GALKA", new RaceRule((short) 5, true, false)
    );

    private final int port;
    private final HikariDataSource dataSource;
    private final ExecutorService clientExecutor = Executors.newCachedThreadPool();
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
        server.logQuicStatus();
        server.start();
    }

    private void start() throws IOException {
        scheduler.scheduleAtFixedRate(this::cleanupSessions, 10, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::cleanupAuthTickets, 10, 10, TimeUnit.SECONDS);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            LOGGER.info("FFXI minimal server listening on port {}", port);
            while (true) {
                Socket socket = serverSocket.accept();
                clientExecutor.submit(() -> handleClient(socket));
            }
        } finally {
            scheduler.shutdownNow();
            clientExecutor.shutdownNow();
            dataSource.close();
        }
    }

    private void handleClient(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            String line = in.readLine();
            if (line == null || line.isBlank()) {
                return;
            }

            MessageFrame request = WireCodec.decode(line);
            MessageFrame response = switch (request.type()) {
                case "LOGIN" -> handleLogin(request);
                case "CHAR_LIST" -> handleCharacterList(request);
                case "CHAR_CREATE" -> handleCharacterCreate(request);
                case "CHAR_DELETE" -> handleCharacterDelete(request);
                case "CHAR_SELECT" -> handleCharacterSelect(request);
                case "PLAY" -> handlePlay(request);
                case "PING" -> handlePing(request);
                case "LOGOUT" -> handleLogout(request);
                default -> error("UNKNOWN_REQUEST", "Unsupported message type: " + request.type());
            };

            out.write(WireCodec.encode(response.type(), response.fields()));
            out.newLine();
            out.flush();
        } catch (Exception e) {
            LOGGER.error("Client handling failed", e);
        }
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
                boolean passwordValid = argon2.verify(passwordHash, password.toCharArray());
                if (!passwordValid) {
                    LOGGER.info("LOGIN_ERR user={} account={} reason=bad_password", username, accountId);
                    return loginError("INVALID_CREDENTIALS", "Invalid username or password");
                }

                String authToken = UUID.randomUUID().toString();
                long expiresAtMs = System.currentTimeMillis() + Duration.ofSeconds(AUTH_TICKET_TIMEOUT_SECONDS).toMillis();
                authTickets.put(authToken, new AuthTicket(accountId, expiresAtMs));
                LOGGER.info("LOGIN_OK user={} account={} authToken={} expiresInSeconds={}",
                    username, accountId, authToken, AUTH_TICKET_TIMEOUT_SECONDS);

                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("code", "OK");
                fields.put("message", "Authenticated");
                fields.put("authToken", authToken);
                fields.put("accountId", Long.toString(accountId));
                return new MessageFrame("LOGIN_OK", fields);
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
                 SELECT id, name, race, gender, face, starting_city, current_zone_id
                 FROM characters
                 WHERE account_id = ? AND deleted_at IS NULL
                 ORDER BY id
                 """)) {
            statement.setLong(1, accountId);
            try (ResultSet rs = statement.executeQuery()) {
                Map<String, String> fields = new LinkedHashMap<>();
                int index = 0;
                while (rs.next()) {
                    fields.put("char" + index + "_id", Long.toString(rs.getLong("id")));
                    fields.put("char" + index + "_name", rs.getString("name"));
                    int raceId = rs.getInt("race");
                    fields.put("char" + index + "_race", Integer.toString(raceId));
                    fields.put("char" + index + "_raceName", raceNameForId(raceId));
                    fields.put("char" + index + "_gender", normalizeGender(rs.getString("gender")));
                    fields.put("char" + index + "_face", Integer.toString(rs.getInt("face")));
                    fields.put("char" + index + "_city", rs.getString("starting_city"));
                    fields.put("char" + index + "_zone", Integer.toString(rs.getInt("current_zone_id")));
                    index++;
                }
                fields.put("count", Integer.toString(index));
                LOGGER.info("CHAR_LIST_OK account={} count={}", accountId, index);
                return new MessageFrame("CHAR_LIST_OK", fields);
            }
        } catch (SQLException e) {
            LOGGER.error("CHAR_LIST_ERR account={} reason=db_error", accountId, e);
            return error("SERVER_ERROR", "Failed to load characters");
        }
    }

    private MessageFrame handleCharacterCreate(MessageFrame frame) {
        Long accountId = authenticateTicket(frame.get("authToken"));
        if (accountId == null) {
            return error("UNAUTHORIZED", "Invalid or expired auth token");
        }

        String name = normalize(frame.get("name"));
        String raceName = normalize(frame.get("race")).toUpperCase(Locale.ROOT);
        RaceRule raceRule = RACE_RULES.get(raceName);
        String gender = parseGenderForCreate(frame.get("gender"));
        int face = parseInt(frame.get("face"), -1);
        String city = normalize(frame.get("city")).toUpperCase(Locale.ROOT);

        if (!CHARACTER_NAME_PATTERN.matcher(name).matches()) {
            return error("INVALID_NAME", "Character name must be 3-15 letters (A-Z)");
        }
        if (raceRule == null) {
            return error("INVALID_RACE", "Race must be HUME, ELVAAN, TARUTARU, MITHRA, or GALKA");
        }
        if (gender == null) {
            return error("INVALID_GENDER", "Gender must be M or F");
        }
        if (!raceRule.allows(gender)) {
            return error("INVALID_GENDER", raceName + " does not support gender " + gender);
        }
        if (face < 1 || face > 8) {
            return error("INVALID_FACE", "Face must be in range 1..8");
        }
        CharacterStartLocation location = STARTING_CITIES.get(city);
        if (location == null) {
            return error("INVALID_STARTING_CITY", "Starting city must be BASTOK, SANDORIA, or WINDURST");
        }

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 INSERT INTO characters (
                   account_id, name, race, gender, face, starting_city, home_zone_id, home_x, home_y, home_z, home_rot,
                   current_zone_id, current_x, current_y, current_z, current_rot
                 ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                 RETURNING id
                 """)) {
            statement.setLong(1, accountId);
            statement.setString(2, name);
            statement.setShort(3, raceRule.id());
            statement.setString(4, gender);
            statement.setInt(5, face);
            statement.setString(6, city);
            statement.setInt(7, location.zoneId());
            statement.setFloat(8, location.x());
            statement.setFloat(9, location.y());
            statement.setFloat(10, location.z());
            statement.setFloat(11, location.rot());
            statement.setInt(12, location.zoneId());
            statement.setFloat(13, location.x());
            statement.setFloat(14, location.y());
            statement.setFloat(15, location.z());
            statement.setFloat(16, location.rot());

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return error("SERVER_ERROR", "Failed to create character");
                }
                long characterId = rs.getLong("id");
                LOGGER.info("CHAR_CREATE_OK account={} characterId={} name={} race={} gender={} face={} city={}",
                    accountId, characterId, name, raceName, gender, face, city);

                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("characterId", Long.toString(characterId));
                fields.put("name", name);
                return new MessageFrame("CHAR_CREATE_OK", fields);
            }
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                LOGGER.info("CHAR_CREATE_ERR account={} reason=duplicate_name name={}", accountId, name);
                return error("NAME_ALREADY_TAKEN", "Character name is already in use");
            }
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
            try (PreparedStatement activeSessionCheck = connection.prepareStatement(
                "SELECT 1 FROM accounts_sessions WHERE character_id = ? LIMIT 1")) {
                activeSessionCheck.setLong(1, characterId);
                try (ResultSet rs = activeSessionCheck.executeQuery()) {
                    if (rs.next()) {
                        return error("CHARACTER_ACTIVE", "Character is currently online");
                    }
                }
            }

            try (PreparedStatement softDelete = connection.prepareStatement("""
                UPDATE characters
                SET deleted_at = NOW()
                WHERE id = ? AND account_id = ? AND deleted_at IS NULL
                """)) {
                softDelete.setLong(1, characterId);
                softDelete.setLong(2, accountId);
                int updated = softDelete.executeUpdate();
                if (updated == 0) {
                    return error("CHARACTER_NOT_FOUND", "Character not found");
                }
            }
            LOGGER.info("CHAR_DELETE_OK account={} characterId={}", accountId, characterId);
            return new MessageFrame("CHAR_DELETE_OK", Map.of("characterId", Long.toString(characterId)));
        } catch (SQLException e) {
            LOGGER.error("CHAR_DELETE_ERR account={} characterId={} reason=db_error", accountId, characterId, e);
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
            fields.put("characterId", Long.toString(characterId));
            fields.put("characterName", identity.name());
            fields.put("homeZoneId", Integer.toString(identity.homeZoneId()));
            fields.put("currentZoneId", Integer.toString(identity.currentZoneId()));
            fields.put("x", Float.toString(identity.currentX()));
            fields.put("y", Float.toString(identity.currentY()));
            fields.put("z", Float.toString(identity.currentZ()));
            fields.put("rot", Float.toString(identity.currentHeading()));
            LOGGER.info("CHAR_SELECT_OK account={} characterId={} zone={} (ready_for_play)",
                accountId, characterId, identity.currentZoneId());
            return new MessageFrame("CHAR_SELECT_OK", fields);
        } catch (SQLException e) {
            LOGGER.error("CHAR_SELECT_ERR account={} characterId={} reason=db_error", accountId, characterId, e);
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
            fields.put("sessionId", sessionId);
            fields.put("accountId", Long.toString(accountId));
            fields.put("characterId", Long.toString(characterId));
            fields.put("characterName", identity.name());
            fields.put("zoneId", Integer.toString(identity.currentZoneId()));
            fields.put("playersInZone", Integer.toString(playersInZone));
            fields.put("homeZoneId", Integer.toString(identity.homeZoneId()));
            fields.put("currentZoneId", Integer.toString(identity.currentZoneId()));
            fields.put("x", Float.toString(identity.currentX()));
            fields.put("y", Float.toString(identity.currentY()));
            fields.put("z", Float.toString(identity.currentZ()));
            fields.put("rot", Float.toString(identity.currentHeading()));
            LOGGER.info("PLAY_OK account={} characterId={} session={} zone={} playersInZone={}",
                accountId, characterId, sessionId, identity.currentZoneId(), playersInZone);
            return new MessageFrame("PLAY_OK", fields);
        } catch (SQLException e) {
            LOGGER.error("PLAY_ERR account={} characterId={} reason=db_error", accountId, characterId, e);
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
            int updated = statement.executeUpdate();
            if (updated == 0) {
                return error("SESSION_NOT_FOUND", "Session not found");
            }
            return new MessageFrame("PONG", Map.of("sessionId", sessionId));
        } catch (SQLException e) {
            LOGGER.error("PING_ERR session={} reason=db_error", sessionId, e);
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
            LOGGER.error("LOGOUT_ERR session={} reason=db_error", sessionId, e);
            return error("SERVER_ERROR", "Failed to close session");
        }
    }

    private void cleanupSessions() {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement selectStale = connection.prepareStatement("""
                 SELECT session_id, zone_id
                 FROM accounts_sessions
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
                LOGGER.info("SESSION_CLEANUP removed={} timeoutSeconds={}", removed, SESSION_TIMEOUT_SECONDS);
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
            SELECT
              id,
              name,
              home_zone_id,
              home_x,
              home_y,
              home_z,
              home_rot,
              current_zone_id,
              current_x,
              current_y,
              current_z,
              current_rot
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
                  zone_id INT NOT NULL,
                  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                  UNIQUE (account_id),
                  UNIQUE (character_id)
                );
                ALTER TABLE accounts_sessions ADD COLUMN IF NOT EXISTS zone_id INT NOT NULL DEFAULT 0;
                CREATE INDEX IF NOT EXISTS idx_accounts_sessions_last_seen ON accounts_sessions(last_seen_at);
                CREATE TABLE IF NOT EXISTS characters (
                  id BIGSERIAL PRIMARY KEY,
                  account_id BIGINT NOT NULL REFERENCES accounts(id),
                  name VARCHAR(16) NOT NULL,
                  race SMALLINT NOT NULL,
                  gender CHAR(1) NOT NULL DEFAULT 'M',
                  face SMALLINT NOT NULL,
                  starting_city VARCHAR(16) NOT NULL,
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
                ALTER TABLE characters ADD COLUMN IF NOT EXISTS gender CHAR(1) NOT NULL DEFAULT 'M';
                CREATE UNIQUE INDEX IF NOT EXISTS idx_characters_name_active
                  ON characters (LOWER(name))
                  WHERE deleted_at IS NULL;
                CREATE INDEX IF NOT EXISTS idx_characters_account_active
                  ON characters (account_id)
                  WHERE deleted_at IS NULL;
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
            LOGGER.info("Bootstrapped account username=dev password=dev hash={}", hash);
        }
    }

    private void logQuicStatus() {
        try {
            Class.forName("io.netty.incubator.codec.quic.Quic");
            LOGGER.info("QUIC stack detected: Netty incubator codec classes available");
        } catch (ClassNotFoundException e) {
            LOGGER.warn("QUIC stack unavailable on classpath");
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

    private MessageFrame loginError(String code, String message) {
        return new MessageFrame("LOGIN_ERR", Map.of("code", code, "message", message));
    }

    private MessageFrame error(String code, String message) {
        return new MessageFrame("ERROR", Map.of("code", code, "message", message));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeGender(String value) {
        if ("F".equalsIgnoreCase(normalize(value))) {
            return "F";
        }
        return "M";
    }

    private String parseGenderForCreate(String value) {
        String normalized = normalize(value).toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "M", "F" -> normalized;
            default -> null;
        };
    }

    private String raceNameForId(int raceId) {
        return switch (raceId) {
            case 1 -> "HUME";
            case 2 -> "ELVAAN";
            case 3 -> "TARUTARU";
            case 4 -> "MITHRA";
            case 5 -> "GALKA";
            default -> "UNKNOWN";
        };
    }

    private int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private int joinZone(String sessionId, int zoneId) {
        sessionZones.put(sessionId, zoneId);
        int playersInZone = zonePopulation.computeIfAbsent(zoneId, ignored -> new AtomicInteger(0)).incrementAndGet();
        LOGGER.info("ZONE_ENTER zone={} session={} playersInZone={}", zoneId, sessionId, playersInZone);
        return playersInZone;
    }

    private void leaveZone(String sessionId, Integer zoneIdHint) {
        Integer zoneId = zoneIdHint != null ? zoneIdHint : sessionZones.get(sessionId);
        Integer mappedZoneId = sessionZones.remove(sessionId);
        if (mappedZoneId != null) {
            zoneId = mappedZoneId;
        }
        if (zoneId == null) {
            return;
        }

        AtomicInteger count = zonePopulation.get(zoneId);
        if (count == null) {
            return;
        }

        int playersInZone = count.decrementAndGet();
        if (playersInZone <= 0) {
            zonePopulation.remove(zoneId, count);
            playersInZone = 0;
        }
        LOGGER.info("ZONE_LEAVE zone={} session={} playersInZone={}", zoneId, sessionId, playersInZone);
    }

    private record AuthTicket(long accountId, long expiresAtMs) {
    }

    private record CharacterStartLocation(int zoneId, float x, float y, float z, float rot) {
    }

    private record RaceRule(short id, boolean maleAllowed, boolean femaleAllowed) {
        boolean allows(String gender) {
            return "F".equals(gender) ? femaleAllowed : maleAllowed;
        }
    }
}
