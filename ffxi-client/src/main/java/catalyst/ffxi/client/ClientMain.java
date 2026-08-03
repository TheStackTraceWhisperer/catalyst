package catalyst.ffxi.client;

import catalyst.ffxi.common.model.RuntimeMode;
import catalyst.ffxi.common.net.MessageFrame;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCond;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSetErrorCallback;
import static org.lwjgl.glfw.GLFW.glfwShowWindow;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.system.MemoryUtil.NULL;

public final class ClientMain {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final long KEEPALIVE_INTERVAL_MS = 5_000L;
    private static final String[] STARTING_CITIES = {"BASTOK", "SANDORIA", "WINDURST"};
    private static final String[] RACE_OPTIONS = {"Hume", "Elvaan", "Tarutaru", "Mithra", "Galka"};
    private static final String[] GENDER_OPTIONS = {"Male", "Female"};

    private final ImString host = new ImString("127.0.0.1", 128);
    private final ImInt port = new ImInt(35555);
    private final ImString username = new ImString("dev", 64);
    private final ImString password = new ImString("dev", 64);
    private final ImString newCharacterName = new ImString("", 32);
    private final ImInt raceIndex = new ImInt(0);
    private final ImInt genderIndex = new ImInt(0);
    private final ImInt face = new ImInt(1);
    private final ImInt cityIndex = new ImInt(0);
    private final ImBoolean autoScroll = new ImBoolean(true);

    private final List<String> logs = new ArrayList<>();
    private final List<RemoteCharacter> characters = new ArrayList<>();

    private RuntimeMode mode = RuntimeMode.LOCAL;
    private String status = "Not connected";
    private String authToken = "-";
    private String accountId = "-";
    private String selectedCharacterId = "-";
    private String selectedCharacterName = "-";
    private String sessionId = "-";
    private String keepAliveStatus = "idle";
    private boolean createCharacterFormVisible = false;
    private long lastKeepAliveAtMs = 0L;
    private long lastKeepAliveOkAtMs = 0L;
    private long lastKeepAliveLatencyMs = -1L;

    public static void main(String[] args) {
        new ClientMain().run();
    }

    private void run() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);

        long window = glfwCreateWindow(1280, 720, "FFXI Minimal Client", NULL, NULL);
        if (window == NULL) {
            throw new IllegalStateException("Failed to create GLFW window");
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();

        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);

        ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
        ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();
        imGuiGlfw.init(window, true);
        imGuiGl3.init("#version 150");

        log("Client started in LOCAL mode");
        log(quicStackStatus());

        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents();
            maybeKeepAlive();
            imGuiGlfw.newFrame();
            ImGui.newFrame();

            drawControlWindow();
            drawLogWindow();

            ImGui.render();
            int[] width = new int[1];
            int[] height = new int[1];
            glfwGetFramebufferSize(window, width, height);
            glViewport(0, 0, width[0], height[0]);
            glClearColor(0.07f, 0.07f, 0.09f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
            imGuiGl3.renderDrawData(ImGui.getDrawData());
            glfwSwapBuffers(window);
        }

        gracefulDisconnect("window close");

        imGuiGl3.dispose();
        imGuiGlfw.dispose();
        ImGui.destroyContext();
        glfwDestroyWindow(window);
        glfwTerminate();

        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
    }

    private void drawControlWindow() {
        ImGui.setNextWindowPos(20, 20, ImGuiCond.Once);
        ImGui.setNextWindowSize(760, 520, ImGuiCond.Once);
        ImGui.begin("Milestone 1 Client");

        if (ImGui.radioButton("Local Mode", mode == RuntimeMode.LOCAL)) {
            gracefulDisconnect("mode switch to local");
            mode = RuntimeMode.LOCAL;
            clearRemoteAuth();
            status = "Local mode";
            keepAliveStatus = "offline";
            log("Switched runtime mode to LOCAL");
        }
        ImGui.sameLine();
        if (ImGui.radioButton("Remote Mode", mode == RuntimeMode.REMOTE)) {
            mode = RuntimeMode.REMOTE;
            status = "Remote mode";
            keepAliveStatus = "waiting for login";
            log("Switched runtime mode to REMOTE");
        }

        ImGui.separator();
        if (mode == RuntimeMode.REMOTE && hasAuthToken()) {
            ImGui.beginDisabled();
        }
        ImGui.inputText("Host", host);
        ImGui.inputInt("Port", port);
        if (mode == RuntimeMode.REMOTE && hasAuthToken()) {
            ImGui.endDisabled();
        }

        if (mode == RuntimeMode.LOCAL) {
            if (ImGui.button("Enter Local Zone")) {
                status = "Local zone loaded: zone=230 char=LocalDev";
                sessionId = "LOCAL-" + System.currentTimeMillis();
                keepAliveStatus = "offline";
                log("Entered local-only zone bootstrap with preset character LocalDev");
            }
        } else {
            drawRemoteControls();
        }

        ImGui.separator();
        ImGui.text("Mode: " + mode);
        ImGui.text("Status: " + status);
        ImGui.text("Account: " + accountId);
        ImGui.text("Auth Token: " + (hasAuthToken() ? "<set>" : "<none>"));
        ImGui.text("Selected Character: " + selectedCharacterName);
        ImGui.text("Session: " + sessionId);
        ImGui.text("KeepAlive: " + keepAliveStatus);
        ImGui.text("Last RTT: " + (lastKeepAliveLatencyMs >= 0 ? lastKeepAliveLatencyMs + "ms" : "-"));
        ImGui.text("Last OK: " + (lastKeepAliveOkAtMs == 0 ? "-" : formatTime(lastKeepAliveOkAtMs)));
        ImGui.end();
    }

    private void drawRemoteControls() {
        if (!hasAuthToken()) {
            ImGui.inputText("Username", username);
            ImGui.inputText("Password", password);
            if (ImGui.button("Login Remote")) {
                doRemoteLogin();
            }
            return;
        }

        if (hasActiveRemoteSession()) {
            ImGui.text("In game as: " + selectedCharacterName);
            ImGui.separator();
            if (ImGui.button("Ping now")) {
                sendKeepAlive();
            }
            ImGui.sameLine();
            if (ImGui.button("Logout Session")) {
                logoutRemoteSession();
            }
            return;
        }

        if (ImGui.button("Refresh Characters")) {
            refreshCharacters();
        }
        ImGui.sameLine();
        if (ImGui.button("Sign Out")) {
            gracefulDisconnect("sign out");
            clearRemoteAuth();
            status = "Signed out";
            log("Signed out of remote account");
        }

        ImGui.separator();
        if (!createCharacterFormVisible) {
            if (ImGui.button("Create Character")) {
                createCharacterFormVisible = true;
            }
        } else {
            ImGui.text("Create Character");
            ImGui.inputText("Name", newCharacterName);
            ImGui.combo("Race", raceIndex, RACE_OPTIONS);
            if (isMithraSelected()) {
                genderIndex.set(1);
                ImGui.text("Gender: Female (Mithra only)");
            } else if (isGalkaSelected()) {
                genderIndex.set(0);
                ImGui.text("Gender: Male (Galka only)");
            } else {
                ImGui.combo("Gender", genderIndex, GENDER_OPTIONS);
            }
            ImGui.inputInt("Face (1-8)", face);
            ImGui.combo("Starting City", cityIndex, STARTING_CITIES);
            if (ImGui.button("Create")) {
                createCharacter();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                createCharacterFormVisible = false;
            }
        }

        ImGui.separator();
        ImGui.text("Characters");
        if (characters.isEmpty()) {
            ImGui.textDisabled("No characters yet.");
        } else {
            for (RemoteCharacter rc : characters) {
                ImGui.text("[" + rc.characterId() + "] " + rc.name()
                    + " race=" + rc.raceName() + " gender=" + rc.gender() + " face=" + rc.face()
                    + " city=" + rc.startingCity());
                ImGui.sameLine();
                if (ImGui.smallButton("Select##" + rc.characterId())) {
                    selectCharacter(rc.characterId(), rc.name());
                }
                ImGui.sameLine();
                if (ImGui.smallButton("Delete##" + rc.characterId())) {
                    deleteCharacter(rc.characterId(), rc.name());
                }
            }
        }

        if (hasSelectedCharacter()) {
            ImGui.separator();
            if (ImGui.button("Play")) {
                playSelectedCharacter();
            }
            ImGui.sameLine();
            ImGui.text("Ready: " + selectedCharacterName);
        }
    }

    private void doRemoteLogin() {
        try {
            MessageFrame frame = RemoteGateway.login(host.get(), port.get(), username.get(), password.get());
            if ("LOGIN_OK".equals(frame.type())) {
                authToken = frame.get("authToken");
                accountId = frame.get("accountId");
                selectedCharacterId = "-";
                selectedCharacterName = "-";
                createCharacterFormVisible = false;
                status = "Authenticated. Select or create a character.";
                keepAliveStatus = "waiting for character select";
                log("LOGIN_OK account=" + accountId + " authToken=<set>");
                refreshCharacters();
            } else {
                status = frame.get("code") + ": " + frame.get("message");
                clearRemoteAuth();
                log("LOGIN_ERR code=" + frame.get("code") + " message=" + frame.get("message"));
            }
        } catch (Exception e) {
            status = "Connection error";
            clearRemoteAuth();
            log("ERROR remote login failed: " + e.getMessage());
        }
    }

    private void refreshCharacters() {
        if (!hasAuthToken()) {
            return;
        }
        try {
            MessageFrame frame = RemoteGateway.listCharacters(host.get(), port.get(), authToken);
            if (!"CHAR_LIST_OK".equals(frame.type())) {
                status = frame.get("code") + ": " + frame.get("message");
                log("CHAR_LIST_ERR code=" + frame.get("code") + " message=" + frame.get("message"));
                return;
            }

            characters.clear();
            int count = parseInt(frame.get("count"), 0);
            for (int i = 0; i < count; i++) {
                characters.add(new RemoteCharacter(
                    frame.get("char" + i + "_id"),
                    frame.get("char" + i + "_name"),
                    normalizeRaceName(frame.get("char" + i + "_raceName"), parseInt(frame.get("char" + i + "_race"), 0)),
                    normalizeGender(frame.get("char" + i + "_gender")),
                    parseInt(frame.get("char" + i + "_face"), 1),
                    frame.get("char" + i + "_city")
                ));
            }
            status = "Character list refreshed (" + characters.size() + ")";
            log("CHAR_LIST_OK count=" + characters.size());
        } catch (Exception e) {
            status = "Character list error";
            log("CHAR_LIST_ERR " + e.getMessage());
        }
    }

    private void createCharacter() {
        if (!hasAuthToken() || hasActiveRemoteSession()) {
            return;
        }
        String name = newCharacterName.get();
        String raceName = raceWireValue(raceIndex.get());
        String gender = genderWireValue(genderIndex.get());
        int f = face.get();
        String city = STARTING_CITIES[Math.max(0, Math.min(cityIndex.get(), STARTING_CITIES.length - 1))];
        try {
            MessageFrame frame = RemoteGateway.createCharacter(host.get(), port.get(), authToken, name, raceName, gender, f, city);
            if ("CHAR_CREATE_OK".equals(frame.type())) {
                status = "Character created: " + frame.get("name");
                log("CHAR_CREATE_OK id=" + frame.get("characterId") + " name=" + frame.get("name"));
                newCharacterName.set("");
                createCharacterFormVisible = false;
                refreshCharacters();
            } else {
                status = frame.get("code") + ": " + frame.get("message");
                log("CHAR_CREATE_ERR code=" + frame.get("code") + " message=" + frame.get("message"));
            }
        } catch (Exception e) {
            status = "Character create error";
            log("CHAR_CREATE_ERR " + e.getMessage());
        }
    }

    private void deleteCharacter(String characterId, String characterName) {
        if (!hasAuthToken() || hasActiveRemoteSession()) {
            return;
        }
        try {
            MessageFrame frame = RemoteGateway.deleteCharacter(host.get(), port.get(), authToken, characterId);
            if ("CHAR_DELETE_OK".equals(frame.type())) {
                status = "Character deleted: " + characterName;
                log("CHAR_DELETE_OK id=" + characterId + " name=" + characterName);
                if (characterId.equals(selectedCharacterId)) {
                    selectedCharacterId = "-";
                    selectedCharacterName = "-";
                }
                refreshCharacters();
            } else {
                status = frame.get("code") + ": " + frame.get("message");
                log("CHAR_DELETE_ERR code=" + frame.get("code") + " message=" + frame.get("message"));
            }
        } catch (Exception e) {
            status = "Character delete error";
            log("CHAR_DELETE_ERR " + e.getMessage());
        }
    }

    private void selectCharacter(String characterId, String characterName) {
        if (!hasAuthToken() || hasActiveRemoteSession()) {
            return;
        }
        try {
            MessageFrame frame = RemoteGateway.selectCharacter(host.get(), port.get(), authToken, characterId);
            if ("CHAR_SELECT_OK".equals(frame.type())) {
                selectedCharacterId = characterId;
                selectedCharacterName = characterName;
                status = "Character selected: " + characterName;
                keepAliveStatus = "ready to play";
                log("CHAR_SELECT_OK character=" + characterName + " zone=" + frame.get("currentZoneId") + " (awaiting Play)");
            } else {
                status = frame.get("code") + ": " + frame.get("message");
                log("CHAR_SELECT_ERR code=" + frame.get("code") + " message=" + frame.get("message"));
            }
        } catch (Exception e) {
            status = "Character select error";
            log("CHAR_SELECT_ERR " + e.getMessage());
        }
    }

    private void playSelectedCharacter() {
        if (!hasAuthToken() || !hasSelectedCharacter() || hasActiveRemoteSession()) {
            return;
        }
        try {
            MessageFrame frame = RemoteGateway.play(host.get(), port.get(), authToken, selectedCharacterId);
            if ("PLAY_OK".equals(frame.type())) {
                sessionId = frame.get("sessionId");
                keepAliveStatus = "connected";
                lastKeepAliveAtMs = 0L;
                status = "Playing as " + selectedCharacterName;
                log("PLAY_OK session=" + sessionId + " zone=" + frame.get("zoneId")
                    + " playersInZone=" + frame.get("playersInZone"));
            } else {
                status = frame.get("code") + ": " + frame.get("message");
                log("PLAY_ERR code=" + frame.get("code") + " message=" + frame.get("message"));
            }
        } catch (Exception e) {
            status = "Play request error";
            log("PLAY_ERR " + e.getMessage());
        }
    }

    private void maybeKeepAlive() {
        if (mode != RuntimeMode.REMOTE || !hasActiveRemoteSession()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastKeepAliveAtMs >= KEEPALIVE_INTERVAL_MS) {
            sendKeepAlive();
        }
    }

    private void sendKeepAlive() {
        if (mode != RuntimeMode.REMOTE || !hasActiveRemoteSession()) {
            return;
        }
        long startedAt = System.currentTimeMillis();
        lastKeepAliveAtMs = startedAt;
        try {
            MessageFrame frame = RemoteGateway.ping(host.get(), port.get(), sessionId);
            long elapsed = System.currentTimeMillis() - startedAt;
            if ("PONG".equals(frame.type())) {
                keepAliveStatus = "ok";
                lastKeepAliveOkAtMs = System.currentTimeMillis();
                lastKeepAliveLatencyMs = elapsed;
                log("PONG session=" + sessionId + " rtt=" + elapsed + "ms");
            } else {
                keepAliveStatus = frame.get("code") + ": " + frame.get("message");
                log("PING_ERR code=" + frame.get("code") + " message=" + frame.get("message"));
            }
        } catch (Exception e) {
            keepAliveStatus = "failed";
            log("PING_ERR " + e.getMessage());
        }
    }

    private void logoutRemoteSession() {
        if (!hasActiveRemoteSession()) {
            return;
        }
        String activeSession = sessionId;
        try {
            MessageFrame frame = RemoteGateway.logout(host.get(), port.get(), activeSession);
            if ("BYE".equals(frame.type())) {
                log("LOGOUT_OK session=" + activeSession);
            } else {
                log("LOGOUT_ERR code=" + frame.get("code") + " message=" + frame.get("message"));
            }
        } catch (Exception e) {
            log("LOGOUT_ERR " + e.getMessage());
        } finally {
            clearRemoteSessionState("Logged out");
        }
    }

    private void gracefulDisconnect(String reason) {
        if (!hasActiveRemoteSession()) {
            return;
        }
        String activeSession = sessionId;
        try {
            MessageFrame frame = RemoteGateway.logout(host.get(), port.get(), activeSession);
            if ("BYE".equals(frame.type())) {
                log("GRACEFUL_DISCONNECT session=" + activeSession + " reason=" + reason);
            } else {
                log("GRACEFUL_DISCONNECT_ERR code=" + frame.get("code") + " message=" + frame.get("message"));
            }
        } catch (Exception e) {
            log("GRACEFUL_DISCONNECT_ERR " + e.getMessage());
        } finally {
            clearRemoteSessionState("Disconnected");
        }
    }

    private boolean hasAuthToken() {
        return authToken != null && !authToken.equals("-");
    }

    private boolean hasActiveRemoteSession() {
        return sessionId != null && !sessionId.equals("-") && mode == RuntimeMode.REMOTE;
    }

    private boolean hasSelectedCharacter() {
        return selectedCharacterId != null && !selectedCharacterId.equals("-");
    }

    private void clearRemoteAuth() {
        authToken = "-";
        accountId = "-";
        selectedCharacterId = "-";
        selectedCharacterName = "-";
        createCharacterFormVisible = false;
        characters.clear();
        clearRemoteSessionState("Signed out");
    }

    private void clearRemoteSessionState(String newStatus) {
        status = newStatus;
        sessionId = "-";
        keepAliveStatus = hasAuthToken() ? "waiting for character select" : "disconnected";
        lastKeepAliveAtMs = 0L;
        lastKeepAliveOkAtMs = 0L;
        lastKeepAliveLatencyMs = -1L;
    }

    private void drawLogWindow() {
        ImGui.setNextWindowPos(800, 20, ImGuiCond.Once);
        ImGui.setNextWindowSize(460, 680, ImGuiCond.Once);
        ImGui.begin("Debug Log Viewer");
        ImGui.checkbox("Auto scroll", autoScroll);
        ImGui.sameLine();
        if (ImGui.button("Clear")) {
            logs.clear();
        }
        ImGui.separator();
        ImGui.beginChild("log-scroll");
        for (String line : logs) {
            ImGui.textUnformatted(line);
        }
        if (autoScroll.get()) {
            ImGui.setScrollHereY(1f);
        }
        ImGui.endChild();
        ImGui.end();
    }

    private void log(String message) {
        logs.add("[" + LocalTime.now().format(TIME_FMT) + "] " + message);
    }

    private String formatTime(long epochMs) {
        return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime().format(TIME_FMT);
    }

    private String quicStackStatus() {
        try {
            Class.forName("io.netty.incubator.codec.quic.Quic");
            return "QUIC stack detected: Netty incubator codec classes available";
        } catch (ClassNotFoundException e) {
            return "QUIC stack missing from classpath";
        }
    }

    private int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private boolean isMithraSelected() {
        return raceIndex.get() == 3;
    }

    private boolean isGalkaSelected() {
        return raceIndex.get() == 4;
    }

    private String raceWireValue(int index) {
        return switch (index) {
            case 0 -> "HUME";
            case 1 -> "ELVAAN";
            case 2 -> "TARUTARU";
            case 3 -> "MITHRA";
            case 4 -> "GALKA";
            default -> "HUME";
        };
    }

    private String genderWireValue(int index) {
        return index == 1 ? "F" : "M";
    }

    private String normalizeRaceName(String raceName, int fallbackRaceId) {
        if (raceName != null && !raceName.isBlank()) {
            return raceName;
        }
        return switch (fallbackRaceId) {
            case 1 -> "HUME";
            case 2 -> "ELVAAN";
            case 3 -> "TARUTARU";
            case 4 -> "MITHRA";
            case 5 -> "GALKA";
            default -> "UNKNOWN";
        };
    }

    private String normalizeGender(String gender) {
        if ("F".equalsIgnoreCase(gender)) {
            return "F";
        }
        return "M";
    }

    private record RemoteCharacter(String characterId, String name, String raceName, String gender, int face, String startingCity) {
    }
}
