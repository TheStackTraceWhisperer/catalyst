package catalyst.ffxi.server;

import catalyst.ffxi.common.model.CharacterIdentity;
import catalyst.ffxi.common.net.AuthCode;
import catalyst.ffxi.common.net.MessageFrame;
import catalyst.ffxi.common.net.WireCodec;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ServerMain {
    private static final int DEFAULT_PORT = 35555;
    private static final Duration SESSION_TIMEOUT = Duration.ofSeconds(60);

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        AuthService authService = new AuthService();
        SessionStore sessionStore = new SessionStore();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(
            () -> sessionStore.cleanExpired(SESSION_TIMEOUT),
            5,
            5,
            TimeUnit.SECONDS
        );

        ExecutorService workerPool = Executors.newCachedThreadPool();
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("FFXI minimal server listening on port " + port);
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
            System.err.println("Connection handler error: " + e.getMessage());
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
            writeFrame(
                out,
                "LOGIN_ERR",
                Map.of("code", AuthCode.AUTH_ALREADY_LOGGED_IN.name(), "message", "Account or character already has an active session")
            );
            return;
        }

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
            writeFrame(out, "ERROR", Map.of("code", "UNAUTHORIZED", "message", "Invalid session"));
            return;
        }
        writeFrame(out, "PONG", Map.of("sessionId", sessionId));
    }

    private static void handleLogout(MessageFrame frame, BufferedWriter out, SessionStore sessionStore) throws IOException {
        String sessionId = frame.get("sessionId");
        sessionStore.remove(sessionId);
        writeFrame(out, "BYE", Map.of("sessionId", sessionId));
    }

    private static void writeFrame(BufferedWriter out, String type, Map<String, String> fields) throws IOException {
        out.write(WireCodec.encode(type, fields));
        out.newLine();
        out.flush();
    }

    private record AccountRecord(String accountId, String username, String password, boolean disabled, boolean banned) {
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
        private final Map<String, AccountRecord> accountsByUsername = new HashMap<>();

        private AuthService() {
            accountsByUsername.put("dev", new AccountRecord("1000", "dev", "dev", false, false));
        }

        AuthResult login(String username, String password) {
            AccountRecord account = accountsByUsername.get(username);
            if (account == null || !account.password().equals(password)) {
                return AuthResult.fail(AuthCode.AUTH_INVALID_CREDENTIALS, "Invalid credentials");
            }
            if (account.disabled()) {
                return AuthResult.fail(AuthCode.AUTH_ACCOUNT_DISABLED, "Account disabled");
            }
            if (account.banned()) {
                return AuthResult.fail(AuthCode.AUTH_ACCOUNT_BANNED, "Account banned");
            }
            return AuthResult.ok(account.accountId());
        }
    }

    private record SessionRecord(
        String sessionId,
        String accountId,
        String characterId,
        String serverAddress,
        int serverPort,
        String clientAddress,
        int clientPort,
        Instant createdAt,
        Instant lastSeenAt
    ) {
        SessionRecord touch() {
            return new SessionRecord(
                sessionId,
                accountId,
                characterId,
                serverAddress,
                serverPort,
                clientAddress,
                clientPort,
                createdAt,
                Instant.now()
            );
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
        private final Map<String, SessionRecord> sessionsBySessionId = new ConcurrentHashMap<>();
        private final Map<String, String> sessionByAccountId = new ConcurrentHashMap<>();
        private final Map<String, String> sessionByCharacterId = new ConcurrentHashMap<>();

        synchronized CreateSessionResult create(
            String accountId,
            String characterId,
            String serverAddress,
            int serverPort,
            String clientAddress,
            int clientPort
        ) {
            if (sessionByAccountId.containsKey(accountId) || sessionByCharacterId.containsKey(characterId)) {
                return CreateSessionResult.conflict();
            }

            String sessionId = UUID.randomUUID().toString();
            SessionRecord record = new SessionRecord(
                sessionId,
                accountId,
                characterId,
                serverAddress,
                serverPort,
                clientAddress,
                clientPort,
                Instant.now(),
                Instant.now()
            );
            sessionsBySessionId.put(sessionId, record);
            sessionByAccountId.put(accountId, sessionId);
            sessionByCharacterId.put(characterId, sessionId);
            System.out.println("SESSION_CREATE account=" + accountId + " char=" + characterId + " session=" + sessionId);
            return CreateSessionResult.ok(sessionId);
        }

        synchronized boolean touch(String sessionId) {
            SessionRecord current = sessionsBySessionId.get(sessionId);
            if (current == null) {
                return false;
            }
            sessionsBySessionId.put(sessionId, current.touch());
            return true;
        }

        synchronized void remove(String sessionId) {
            SessionRecord record = sessionsBySessionId.remove(sessionId);
            if (record == null) {
                return;
            }
            sessionByAccountId.remove(record.accountId());
            sessionByCharacterId.remove(record.characterId());
            System.out.println("SESSION_REMOVE account=" + record.accountId() + " char=" + record.characterId() + " session=" + sessionId);
        }

        synchronized void cleanExpired(Duration timeout) {
            Instant threshold = Instant.now().minus(timeout);
            sessionsBySessionId.values().stream()
                .filter(s -> s.lastSeenAt().isBefore(threshold))
                .map(SessionRecord::sessionId)
                .toList()
                .forEach(this::remove);
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
