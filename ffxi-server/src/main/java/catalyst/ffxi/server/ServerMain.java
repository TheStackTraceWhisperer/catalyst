package catalyst.ffxi.server;

import catalyst.ffxi.common.model.CharacterIdentity;
import catalyst.ffxi.common.net.AuthCode;
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
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ServerMain {
    private static final Logger LOG = LoggerFactory.getLogger(ServerMain.class);
    private static final int DEFAULT_PORT = 35555;
    private static final Duration SESSION_TIMEOUT = Duration.ofSeconds(60);

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        QuicStack.report();

        Database database = Database.fromEnv();
        database.initSchema();
        database.ensureDevAccount();
        LOG.info("Server startup: dbUrl={} dbUser={}", database.jdbcUrl(), database.dbUser());

        AuthService authService = new AuthService(database);
        SessionStore sessionStore = new SessionStore(database);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(
            () -> sessionStore.cleanExpired(SESSION_TIMEOUT),
            5,
            5,
            TimeUnit.SECONDS
        );

        ExecutorService workerPool = Executors.newCachedThreadPool();
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            LOG.info("FFXI minimal server listening on port {}", port);
            while (true) {
                Socket socket = serverSocket.accept();
                workerPool.submit(() -> handleConnection(socket, authService, sessionStore));
            }
        }
    }

    private static void handleConnection(Socket socket, AuthService authService, SessionStore sessionStore) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            String line = in.readLine();
            if (line == null || line.isBlank()) {
                return;
            }

            MessageFrame frame = WireCodec.decode(line);
            switch (frame.type()) {
                case "LOGIN" -> handleLogin(frame, out, authService, sessionStore, socket);
                case "PING" -> handlePing(frame, out, sessionStore);
                case "LOGOUT" -> handleLogout(frame, out, sessionStore);
                default -> writeFrame(out, "ERROR", Map.of("code", "INVALID_REQUEST", "message", "Unknown frame type"));
            }
        } catch (Exception e) {
            LOG.error("Connection handler error: {}", e.getMessage(), e);
        }
    }

    private static void handleLogin(
        MessageFrame frame,
        BufferedWriter out,
        AuthService authService,
        SessionStore sessionStore,
        Socket socket
    ) throws IOException {
        String username = frame.get("username");
        String password = frame.get("password");
        String selectedCharacter = frame.get("character");

        AuthResult authResult = authService.login(username, password);
        if (!authResult.success()) {
            LOG.warn("LOGIN_DENIED user={} code={}", username, authResult.code());
            writeFrame(out, "LOGIN_ERR", Map.of("code", authResult.code().name(), "message", authResult.message()));
            return;
        }

        CharacterIdentity character = CharacterFixtures.resolve(selectedCharacter);
        CreateSessionResult createSession = sessionStore.create(
            authResult.accountId(),
            character.characterId(),
            socket.getLocalAddress().getHostAddress(),
            socket.getLocalPort(),
            socket.getInetAddress().getHostAddress(),
            socket.getPort()
        );

        if (!createSession.success()) {
            LOG.warn("LOGIN_SESSION_CONFLICT account={} character={}", authResult.accountId(), character.characterId());
            writeFrame(
                out,
                "LOGIN_ERR",
                Map.of("code", AuthCode.AUTH_ALREADY_LOGGED_IN.name(), "message", "Account or character already has an active session")
            );
            return;
        }
        LOG.info("LOGIN_OK account={} character={} session={}", authResult.accountId(), character.characterId(), createSession.sessionId());

        Map<String, String> response = new HashMap<>();
        response.put("code", AuthCode.AUTH_SUCCESS.name());
        response.put("message", "Session established");
        response.put("sessionId", createSession.sessionId());
        response.put("accountId", authResult.accountId());
        response.put("characterId", character.characterId());
        response.put("characterName", character.name());
        response.put("homeZoneId", Integer.toString(character.homeZoneId()));
        response.put("homeX", Float.toString(character.homeX()));
        response.put("homeY", Float.toString(character.homeY()));
        response.put("homeZ", Float.toString(character.homeZ()));
        response.put("homeHeading", Float.toString(character.homeHeading()));
        response.put("currentZoneId", Integer.toString(character.currentZoneId()));
        response.put("currentX", Float.toString(character.currentX()));
        response.put("currentY", Float.toString(character.currentY()));
        response.put("currentZ", Float.toString(character.currentZ()));
        response.put("currentHeading", Float.toString(character.currentHeading()));
        writeFrame(out, "LOGIN_OK", response);
    }

    private static void handlePing(MessageFrame frame, BufferedWriter out, SessionStore sessionStore) throws IOException {
        String sessionId = frame.get("sessionId");
        boolean ok = sessionStore.touch(sessionId);
        if (!ok) {
            LOG.warn("PING_REJECT invalid-session={}", sessionId);
            writeFrame(out, "ERROR", Map.of("code", "UNAUTHORIZED", "message", "Invalid session"));
            return;
        }
        LOG.debug("PONG session={}", sessionId);
        writeFrame(out, "PONG", Map.of("sessionId", sessionId));
    }

    private static void handleLogout(MessageFrame frame, BufferedWriter out, SessionStore sessionStore) throws IOException {
        String sessionId = frame.get("sessionId");
        LOG.info("LOGOUT_REQUEST session={}", sessionId);
        sessionStore.remove(sessionId);
        writeFrame(out, "BYE", Map.of("sessionId", sessionId));
    }

    private static void writeFrame(BufferedWriter out, String type, Map<String, String> fields) throws IOException {
        out.write(WireCodec.encode(type, fields));
        out.newLine();
        out.flush();
    }

    private record AuthResult(boolean success, AuthCode code, String message, String accountId) {
        static AuthResult ok(String accountId) {
            return new AuthResult(true, AuthCode.AUTH_SUCCESS, "ok", accountId);
        }

        static AuthResult fail(AuthCode code, String message) {
            return new AuthResult(false, code, message, null);
        }
    }

    private static final class AuthService {
        private final Database database;
        private final Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);

        private AuthService(Database database) {
            this.database = database;
        }

        AuthResult login(String username, String password) {
            if (username == null || password == null) {
                return AuthResult.fail(AuthCode.AUTH_INVALID_CREDENTIALS, "Invalid credentials");
            }

            try (Connection connection = database.dataSource.getConnection()) {
                try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, password_hash, status FROM accounts WHERE login = ? LIMIT 1"
                )) {
                    ps.setString(1, username);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            LOG.warn("AUTH_FAIL user={} reason=no-account", username);
                            return AuthResult.fail(AuthCode.AUTH_INVALID_CREDENTIALS, "Invalid credentials");
                        }

                        String accountId = Long.toString(rs.getLong("id"));
                        String passwordHash = rs.getString("password_hash");
                        int status = rs.getInt("status");
                        boolean valid = passwordHash != null && argon2.verify(passwordHash, password.toCharArray());

                        if (!valid) {
                            LOG.warn("AUTH_FAIL user={} accountId={} reason=bad-password", username, accountId);
                            return AuthResult.fail(AuthCode.AUTH_INVALID_CREDENTIALS, "Invalid credentials");
                        }
                        if (status <= 0) {
                            LOG.warn("AUTH_FAIL user={} accountId={} reason=disabled", username, accountId);
                            return AuthResult.fail(AuthCode.AUTH_ACCOUNT_DISABLED, "Account disabled");
                        }
                        if (isBanned(connection, rs.getLong("id"))) {
                            LOG.warn("AUTH_FAIL user={} accountId={} reason=banned", username, accountId);
                            return AuthResult.fail(AuthCode.AUTH_ACCOUNT_BANNED, "Account banned");
                        }

                        LOG.info("AUTH_OK user={} accountId={} argon2id=true", username, accountId);
                        return AuthResult.ok(accountId);
                    }
                }
            } catch (Exception e) {
                LOG.error("AUTH_ERROR user={} message={}", username, e.getMessage(), e);
                return AuthResult.fail(AuthCode.AUTH_SERVER_ERROR, "Auth server error");
            }
        }

        private boolean isBanned(Connection connection, long accountId) throws SQLException {
            try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM accounts_banned WHERE account_id = ? AND (unban_at IS NULL OR unban_at > NOW()) LIMIT 1"
            )) {
                ps.setLong(1, accountId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }
    }

    private record CreateSessionResult(boolean success, String sessionId) {
        static CreateSessionResult ok(String sessionId) {
            return new CreateSessionResult(true, sessionId);
        }

        static CreateSessionResult conflict() {
            return new CreateSessionResult(false, null);
        }
    }

    private static final class SessionStore {
        private final Database database;

        private SessionStore(Database database) {
            this.database = database;
        }

        CreateSessionResult create(
            String accountId,
            String characterId,
            String serverAddress,
            int serverPort,
            String clientAddress,
            int clientPort
        ) {
            String sessionId = UUID.randomUUID().toString();
            String sessionKey = UUID.randomUUID().toString();

            try (Connection connection = database.dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement check = connection.prepareStatement(
                        "SELECT 1 FROM accounts_sessions WHERE account_id = ? OR character_id = ? LIMIT 1"
                    )) {
                        check.setLong(1, Long.parseLong(accountId));
                        check.setLong(2, Long.parseLong(characterId));
                        try (ResultSet rs = check.executeQuery()) {
                            if (rs.next()) {
                                connection.rollback();
                                return CreateSessionResult.conflict();
                            }
                        }
                    }

                    try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO accounts_sessions(session_id, account_id, character_id, session_key, server_address, server_port, client_address, client_port, version_mismatch, last_zoneout_time, last_seen_at, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), NOW())"
                    )) {
                        insert.setObject(1, UUID.fromString(sessionId));
                        insert.setLong(2, Long.parseLong(accountId));
                        insert.setLong(3, Long.parseLong(characterId));
                        insert.setString(4, sessionKey);
                        insert.setString(5, serverAddress);
                        insert.setInt(6, serverPort);
                        insert.setString(7, clientAddress);
                        insert.setInt(8, clientPort);
                        insert.setBoolean(9, false);
                        insert.executeUpdate();
                    }

                    try (PreparedStatement ipRecord = connection.prepareStatement(
                        "INSERT INTO account_ip_record(login_time, account_id, character_id, client_ip) VALUES (NOW(), ?, ?, ?)"
                    )) {
                        ipRecord.setLong(1, Long.parseLong(accountId));
                        ipRecord.setLong(2, Long.parseLong(characterId));
                        ipRecord.setString(3, clientAddress);
                        ipRecord.executeUpdate();
                    }

                    connection.commit();
                    LOG.info("SESSION_CREATE account={} char={} session={} client={}:{}", accountId, characterId, sessionId, clientAddress, clientPort);
                    return CreateSessionResult.ok(sessionId);
                } catch (SQLException e) {
                    connection.rollback();
                    if (isUniqueViolation(e)) {
                        LOG.warn("SESSION_CONFLICT account={} char={}", accountId, characterId);
                        return CreateSessionResult.conflict();
                    }
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (Exception e) {
                LOG.error("SESSION_CREATE_ERR account={} char={} message={}", accountId, characterId, e.getMessage(), e);
                return CreateSessionResult.conflict();
            }
        }

        boolean touch(String sessionId) {
            try (Connection connection = database.dataSource.getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                     "UPDATE accounts_sessions SET last_seen_at = NOW() WHERE session_id = ?"
                 )) {
                ps.setObject(1, UUID.fromString(sessionId));
                return ps.executeUpdate() > 0;
            } catch (Exception e) {
                LOG.error("SESSION_TOUCH_ERR session={} message={}", sessionId, e.getMessage(), e);
                return false;
            }
        }

        void remove(String sessionId) {
            try (Connection connection = database.dataSource.getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                     "DELETE FROM accounts_sessions WHERE session_id = ?"
                 )) {
                ps.setObject(1, UUID.fromString(sessionId));
                int deleted = ps.executeUpdate();
                if (deleted > 0) {
                    LOG.info("SESSION_REMOVE session={}", sessionId);
                }
            } catch (Exception e) {
                LOG.error("SESSION_REMOVE_ERR session={} message={}", sessionId, e.getMessage(), e);
            }
        }

        void cleanExpired(Duration timeout) {
            Instant threshold = Instant.now().minus(timeout);
            try (Connection connection = database.dataSource.getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                     "DELETE FROM accounts_sessions WHERE last_seen_at < ?"
                 )) {
                ps.setTimestamp(1, Timestamp.from(threshold));
                int deleted = ps.executeUpdate();
                if (deleted > 0) {
                    LOG.info("SESSION_TIMEOUT_CLEANUP count={} threshold={}", deleted, threshold);
                }
            } catch (Exception e) {
                LOG.error("SESSION_TIMEOUT_CLEANUP_ERR threshold={} message={}", threshold, e.getMessage(), e);
            }
        }

        private boolean isUniqueViolation(SQLException e) {
            return "23505".equals(e.getSQLState());
        }
    }

    private static final class Database {
        private final HikariDataSource dataSource;
        private final String jdbcUrl;
        private final String dbUser;

        private Database(HikariDataSource dataSource, String jdbcUrl, String dbUser) {
            this.dataSource = dataSource;
            this.jdbcUrl = jdbcUrl;
            this.dbUser = dbUser;
        }

        static Database fromEnv() {
            String url = System.getenv().getOrDefault("FFXI_DB_URL", "jdbc:postgresql://localhost:5432/ffxi");
            String user = System.getenv().getOrDefault("FFXI_DB_USER", "ffxi");
            String password = System.getenv().getOrDefault("FFXI_DB_PASSWORD", "ffxi");

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(user);
            config.setPassword(password);
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(1);
            config.setPoolName("ffxi-server-db");
            LOG.info("DB_CONNECT initializing pool url={} user={}", url, user);
            return new Database(new HikariDataSource(config), url, user);
        }

        String jdbcUrl() {
            return jdbcUrl;
        }

        String dbUser() {
            return dbUser;
        }

        void initSchema() throws SQLException {
            try (Connection connection = dataSource.getConnection();
                 Statement st = connection.createStatement()) {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS accounts (
                      id BIGSERIAL PRIMARY KEY,
                      login VARCHAR(32) NOT NULL UNIQUE,
                      password_hash TEXT NOT NULL,
                      status SMALLINT NOT NULL DEFAULT 1,
                      priv SMALLINT NOT NULL DEFAULT 1,
                      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS accounts_banned (
                      account_id BIGINT PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
                      banned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                      unban_at TIMESTAMPTZ NULL,
                      ban_comment TEXT NULL
                    )
                    """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS accounts_sessions (
                      session_id UUID PRIMARY KEY,
                      account_id BIGINT NOT NULL UNIQUE REFERENCES accounts(id) ON DELETE CASCADE,
                      character_id BIGINT NOT NULL UNIQUE,
                      session_key TEXT NOT NULL,
                      server_address TEXT NOT NULL,
                      server_port INTEGER NOT NULL,
                      client_address TEXT NOT NULL,
                      client_port INTEGER NOT NULL,
                      version_mismatch BOOLEAN NOT NULL DEFAULT FALSE,
                      last_zoneout_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                      last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);
                st.execute("""
                    CREATE TABLE IF NOT EXISTS account_ip_record (
                      login_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                      account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
                      character_id BIGINT NOT NULL,
                      client_ip TEXT NOT NULL
                    )
                    """);
                LOG.info("DB_SCHEMA_READY tables=accounts,accounts_banned,accounts_sessions,account_ip_record");
            }
        }

        void ensureDevAccount() throws SQLException {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement find = connection.prepareStatement(
                     "SELECT id FROM accounts WHERE login = ? LIMIT 1"
                 )) {
                find.setString(1, "dev");
                try (ResultSet rs = find.executeQuery()) {
                    if (rs.next()) {
                        LOG.info("DB_BOOTSTRAP_ACCOUNT_EXISTS login=dev accountId={}", rs.getLong("id"));
                        return;
                    }
                }

                Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
                String hash = argon2.hash(3, 65_536, 1, "dev".toCharArray());
                try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO accounts(login, password_hash, status, priv) VALUES (?, ?, 1, 1)"
                )) {
                    insert.setString(1, "dev");
                    insert.setString(2, hash);
                    insert.executeUpdate();
                }
                LOG.info("DB_BOOTSTRAP_ACCOUNT_CREATED login=dev argon2id=true");
            }
        }
    }

    private static final class QuicStack {
        private QuicStack() {
        }

        static void report() {
            try {
                Class.forName("io.netty.incubator.codec.quic.Quic");
                LOG.info("QUIC_STACK_DETECTED provider=netty-incubator");
            } catch (ClassNotFoundException e) {
                LOG.warn("QUIC_STACK_MISSING provider=netty-incubator");
            }
        }
    }

    private static final class CharacterFixtures {
        private CharacterFixtures() {
        }

        static CharacterIdentity resolve(String selectedCharacter) {
            String name = (selectedCharacter == null || selectedCharacter.isBlank()) ? "DevCharacter" : selectedCharacter;
            return new CharacterIdentity(
                "2001",
                name,
                230,
                -40.01f,
                1.34f,
                33.87f,
                0f,
                230,
                -40.01f,
                1.34f,
                33.87f,
                0f
            );
        }
    }
}
