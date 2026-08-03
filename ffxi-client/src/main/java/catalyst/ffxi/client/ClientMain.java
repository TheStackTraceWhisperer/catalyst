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

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public final class ClientMain {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final long KEEPALIVE_INTERVAL_MS = 5_000L;

    // LSB race encoding 1-8 (encodes gender)
    private static final String[] RACE_LABELS = {
        "Hume Male", "Hume Female",
        "Elvaan Male", "Elvaan Female",
        "Tarutaru Male", "Tarutaru Female",
        "Mithra", "Galka"
    };
    // size: 0=Small 1=Medium 2=Large
    private static final String[] SIZE_LABELS   = {"Small", "Medium", "Large"};
    // Starting jobs 1-6
    private static final String[] JOB_LABELS    = {"Warrior", "Monk", "White Mage", "Black Mage", "Red Mage", "Thief"};
    private static final String[] NATION_LABELS = {"San d'Oria", "Bastok", "Windurst"};

    // Tarutaru (idx 4,5) forced Small; Galka (idx 7) forced Large
    private static final int[] RACE_FORCED_SIZE = {-1, -1, -1, -1, 0, 0, -1, 2};

    private final ImString host = new ImString("127.0.0.1", 128);
    private final ImInt   portField = new ImInt(35555);
    private final ImString username = new ImString("dev", 64);
    private final ImString password = new ImString("dev", 64);
    private final ImString newCharName = new ImString("", 32);
    private final ImInt    raceIndex  = new ImInt(0);
    private final ImInt    sizeIndex  = new ImInt(1);
    private final ImInt    faceNum    = new ImInt(1);  // 1-8
    private final ImBoolean faceVariantB = new ImBoolean(false); // false=A, true=B
    private final ImInt    jobIndex   = new ImInt(0);
    private final ImInt    nationIndex = new ImInt(0);
    private final ImBoolean autoScroll = new ImBoolean(true);

    private final List<String> logs = new ArrayList<>();
    private final List<RemoteCharacter> characters = new ArrayList<>();

    private RuntimeMode mode = RuntimeMode.LOCAL;
    private String status = "Not connected";
    private String authToken = "-";
    private String accountId = "-";
    private String selectedCharId   = "-";
    private String selectedCharName = "-";
    private String sessionId  = "-";
    private String keepAliveStatus = "idle";
    private boolean createFormVisible = false;
    private long lastPingAtMs   = 0L;
    private long lastPingOkAtMs = 0L;
    private long lastPingRttMs  = -1L;

    public static void main(String[] args) {
        new ClientMain().run();
    }

    private void run() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);

        long window = glfwCreateWindow(1280, 720, "FFXI Client", NULL, NULL);
        if (window == NULL) throw new IllegalStateException("Failed to create GLFW window");
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();

        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);
        ImGuiImplGlfw implGlfw = new ImGuiImplGlfw();
        ImGuiImplGl3  implGl3  = new ImGuiImplGl3();
        implGlfw.init(window, true);
        implGl3.init("#version 150");

        log("Client started (QUIC transport)");
        try {
            Class.forName("io.netty.incubator.codec.quic.Quic");
            log("QUIC stack: Netty incubator classes available");
        } catch (ClassNotFoundException e) {
            log("QUIC stack missing from classpath");
        }

        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents();
            maybeKeepAlive();
            implGlfw.newFrame();
            ImGui.newFrame();
            drawControlWindow();
            drawLogWindow();
            ImGui.render();
            int[] w = new int[1], h = new int[1];
            glfwGetFramebufferSize(window, w, h);
            glViewport(0, 0, w[0], h[0]);
            glClearColor(0.07f, 0.07f, 0.09f, 1f);
            glClear(GL_COLOR_BUFFER_BIT);
            implGl3.renderDrawData(ImGui.getDrawData());
            glfwSwapBuffers(window);
        }

        gracefulDisconnect("window close");
        RemoteGateway.shutdown();
        implGl3.dispose();
        implGlfw.dispose();
        ImGui.destroyContext();
        glfwDestroyWindow(window);
        glfwTerminate();
        GLFWErrorCallback cb = glfwSetErrorCallback(null);
        if (cb != null) cb.free();
    }

    // ── UI ──────────────────────────────────────────────────────────────────

    private void drawControlWindow() {
        ImGui.setNextWindowPos(20, 20, ImGuiCond.Once);
        ImGui.setNextWindowSize(760, 600, ImGuiCond.Once);
        ImGui.begin("FFXI Client");

        if (ImGui.radioButton("Local Mode", mode == RuntimeMode.LOCAL)) {
            gracefulDisconnect("mode switch");
            mode = RuntimeMode.LOCAL;
            clearRemoteAuth();
            status = "Local mode";
            log("Switched to LOCAL mode");
        }
        ImGui.sameLine();
        if (ImGui.radioButton("Remote Mode", mode == RuntimeMode.REMOTE)) {
            mode = RuntimeMode.REMOTE;
            status = "Remote mode";
            keepAliveStatus = "waiting for login";
            log("Switched to REMOTE mode");
        }

        ImGui.separator();
        boolean locked = mode == RuntimeMode.REMOTE && hasAuthToken();
        if (locked) ImGui.beginDisabled();
        ImGui.inputText("Host", host);
        ImGui.inputInt("Port", portField);
        if (locked) ImGui.endDisabled();

        if (mode == RuntimeMode.LOCAL) {
            if (ImGui.button("Enter Local Zone")) {
                sessionId = "LOCAL-" + System.currentTimeMillis();
                status = "Local zone loaded (zone=230, char=LocalDev)";
                log("Entered local-only zone");
            }
        } else {
            drawRemoteControls();
        }

        ImGui.separator();
        ImGui.text("Mode: " + mode);
        ImGui.text("Status: " + status);
        ImGui.text("Account: " + accountId);
        ImGui.text("Auth Token: " + (hasAuthToken() ? "<set>" : "<none>"));
        ImGui.text("Selected: " + selectedCharName);
        ImGui.text("Session: " + sessionId);
        ImGui.text("KeepAlive: " + keepAliveStatus);
        ImGui.text("Last RTT: " + (lastPingRttMs >= 0 ? lastPingRttMs + "ms" : "-"));
        ImGui.text("Last OK: " + (lastPingOkAtMs == 0 ? "-" : formatTime(lastPingOkAtMs)));
        ImGui.end();
    }

    private void drawRemoteControls() {
        if (!hasAuthToken()) {
            ImGui.inputText("Username", username);
            ImGui.inputText("Password", password);
            if (ImGui.button("Login")) doLogin();
            return;
        }

        if (hasActiveSession()) {
            ImGui.text("In game as: " + selectedCharName);
            ImGui.separator();
            if (ImGui.button("Ping"))   sendPing();
            ImGui.sameLine();
            if (ImGui.button("Logout")) logoutSession();
            return;
        }

        if (ImGui.button("Refresh")) refreshCharacters();
        ImGui.sameLine();
        if (ImGui.button("Sign Out")) {
            gracefulDisconnect("sign out");
            clearRemoteAuth();
            log("Signed out");
        }

        ImGui.separator();
        if (!createFormVisible) {
            if (ImGui.button("Create Character")) {
                createFormVisible = true;
            }
        } else {
            drawCreateForm();
        }

        ImGui.separator();
        ImGui.text("Characters:");
        if (characters.isEmpty()) {
            ImGui.textDisabled("No characters.");
        } else {
            for (RemoteCharacter rc : characters) {
                ImGui.text(String.format("[%s] %s  %s  size=%d  face=%d  job=%s  nation=%s",
                    rc.characterId(), rc.name(), rc.raceName(),
                    rc.size(), rc.face(), rc.jobName(), rc.nationName()));
                ImGui.sameLine();
                if (ImGui.smallButton("Select##" + rc.characterId())) selectCharacter(rc);
                ImGui.sameLine();
                if (ImGui.smallButton("Delete##" + rc.characterId())) deleteCharacter(rc.characterId(), rc.name());
            }
        }

        if (hasSelectedCharacter()) {
            ImGui.separator();
            if (ImGui.button("Play")) playSelectedCharacter();
            ImGui.sameLine();
            ImGui.text("Ready: " + selectedCharName);
        }
    }

    private void drawCreateForm() {
        ImGui.text("Create Character");
        ImGui.inputText("Name", newCharName);

        // Race (1-8 LSB, index 0-7)
        ImGui.combo("Race", raceIndex, RACE_LABELS);

        // Size — auto-lock for Tarutaru/Galka
        int forcedSize = RACE_FORCED_SIZE[raceIndex.get()];
        if (forcedSize >= 0) {
            sizeIndex.set(forcedSize);
            ImGui.text("Size: " + SIZE_LABELS[forcedSize] + " (fixed for this race)");
        } else {
            ImGui.combo("Size", sizeIndex, SIZE_LABELS);
        }

        // Face 1-8 + A/B toggle → wire as 0-15
        ImGui.inputInt("Face (1-8)", faceNum);
        ImGui.sameLine();
        ImGui.checkbox("Variant B", faceVariantB);

        // Starting Job
        ImGui.combo("Starting Job", jobIndex, JOB_LABELS);

        // Nation
        ImGui.combo("Nation", nationIndex, NATION_LABELS);

        if (ImGui.button("Create")) createCharacter();
        ImGui.sameLine();
        if (ImGui.button("Cancel")) createFormVisible = false;
    }

    // ── Actions ─────────────────────────────────────────────────────────────

    private void doLogin() {
        try {
            MessageFrame f = RemoteGateway.login(host.get(), portField.get(), username.get(), password.get());
            if ("LOGIN_OK".equals(f.type())) {
                authToken = f.get("authToken");
                accountId = f.get("accountId");
                createFormVisible = false;
                status = "Authenticated";
                log("LOGIN_OK account=" + accountId);
                refreshCharacters();
            } else {
                status = f.get("code") + ": " + f.get("message");
                log("LOGIN_ERR " + f.get("code") + " " + f.get("message"));
            }
        } catch (Exception e) {
            status = "Login failed";
            log("LOGIN_ERR " + e.getMessage());
        }
    }

    private void refreshCharacters() {
        if (!hasAuthToken()) return;
        try {
            MessageFrame f = RemoteGateway.listCharacters(host.get(), portField.get(), authToken);
            if (!"CHAR_LIST_OK".equals(f.type())) {
                status = f.get("code") + ": " + f.get("message");
                log("CHAR_LIST_ERR " + f.get("code"));
                return;
            }
            characters.clear();
            int count = parseInt(f.get("count"), 0);
            for (int i = 0; i < count; i++) {
                characters.add(new RemoteCharacter(
                    f.get("char" + i + "_id"),
                    f.get("char" + i + "_name"),
                    parseInt(f.get("char" + i + "_race"), 1),
                    f.get("char" + i + "_raceName"),
                    parseInt(f.get("char" + i + "_size"), 1),
                    parseInt(f.get("char" + i + "_face"), 0),
                    parseInt(f.get("char" + i + "_mainJob"), 1),
                    f.get("char" + i + "_jobName"),
                    parseInt(f.get("char" + i + "_nation"), 0)
                ));
            }
            status = "Characters loaded (" + characters.size() + ")";
            log("CHAR_LIST_OK count=" + characters.size());
        } catch (Exception e) {
            status = "Character list error";
            log("CHAR_LIST_ERR " + e.getMessage());
        }
    }

    private void createCharacter() {
        if (!hasAuthToken() || hasActiveSession()) return;
        int raceId  = raceIndex.get() + 1;            // UI index 0-7 → LSB 1-8
        int sizeId  = sizeIndex.get();                 // 0-2
        int faceId  = Math.clamp(faceNum.get(), 1, 8) - 1 + (faceVariantB.get() ? 8 : 0); // 0-15
        int jobId   = jobIndex.get() + 1;              // UI index 0-5 → job 1-6
        int nationId = nationIndex.get();              // 0-2
        try {
            MessageFrame f = RemoteGateway.createCharacter(
                host.get(), portField.get(), authToken,
                newCharName.get(), raceId, sizeId, faceId, jobId, Integer.toString(nationId));
            if ("CHAR_CREATE_OK".equals(f.type())) {
                status = "Character created: " + f.get("name");
                log("CHAR_CREATE_OK id=" + f.get("characterId") + " name=" + f.get("name"));
                newCharName.set("");
                createFormVisible = false;
                refreshCharacters();
            } else {
                status = f.get("code") + ": " + f.get("message");
                log("CHAR_CREATE_ERR " + f.get("code") + " " + f.get("message"));
            }
        } catch (Exception e) {
            status = "Create failed";
            log("CHAR_CREATE_ERR " + e.getMessage());
        }
    }

    private void deleteCharacter(String characterId, String charName) {
        if (!hasAuthToken() || hasActiveSession()) return;
        try {
            MessageFrame f = RemoteGateway.deleteCharacter(host.get(), portField.get(), authToken, characterId);
            if ("CHAR_DELETE_OK".equals(f.type())) {
                if (characterId.equals(selectedCharId)) {
                    selectedCharId = "-";
                    selectedCharName = "-";
                }
                status = "Deleted: " + charName;
                log("CHAR_DELETE_OK id=" + characterId);
                refreshCharacters();
            } else {
                status = f.get("code") + ": " + f.get("message");
                log("CHAR_DELETE_ERR " + f.get("code"));
            }
        } catch (Exception e) {
            status = "Delete failed";
            log("CHAR_DELETE_ERR " + e.getMessage());
        }
    }

    private void selectCharacter(RemoteCharacter rc) {
        if (!hasAuthToken() || hasActiveSession()) return;
        try {
            MessageFrame f = RemoteGateway.selectCharacter(host.get(), portField.get(), authToken, rc.characterId());
            if ("CHAR_SELECT_OK".equals(f.type())) {
                selectedCharId   = rc.characterId();
                selectedCharName = rc.name();
                status = "Selected: " + rc.name();
                keepAliveStatus = "ready to play";
                log("CHAR_SELECT_OK " + rc.name() + " zone=" + f.get("currentZoneId"));
            } else {
                status = f.get("code") + ": " + f.get("message");
                log("CHAR_SELECT_ERR " + f.get("code"));
            }
        } catch (Exception e) {
            status = "Select failed";
            log("CHAR_SELECT_ERR " + e.getMessage());
        }
    }

    private void playSelectedCharacter() {
        if (!hasAuthToken() || !hasSelectedCharacter() || hasActiveSession()) return;
        try {
            MessageFrame f = RemoteGateway.play(host.get(), portField.get(), authToken, selectedCharId);
            if ("PLAY_OK".equals(f.type())) {
                sessionId = f.get("sessionId");
                keepAliveStatus = "connected";
                lastPingAtMs = 0L;
                status = "Playing as " + selectedCharName;
                log("PLAY_OK session=" + sessionId + " zone=" + f.get("zoneId")
                    + " playersInZone=" + f.get("playersInZone"));
            } else {
                status = f.get("code") + ": " + f.get("message");
                log("PLAY_ERR " + f.get("code"));
            }
        } catch (Exception e) {
            status = "Play failed";
            log("PLAY_ERR " + e.getMessage());
        }
    }

    private void maybeKeepAlive() {
        if (mode != RuntimeMode.REMOTE || !hasActiveSession()) return;
        if (System.currentTimeMillis() - lastPingAtMs >= KEEPALIVE_INTERVAL_MS) sendPing();
    }

    private void sendPing() {
        if (!hasActiveSession()) return;
        long t0 = System.currentTimeMillis();
        lastPingAtMs = t0;
        try {
            MessageFrame f = RemoteGateway.ping(host.get(), portField.get(), sessionId);
            long rtt = System.currentTimeMillis() - t0;
            if ("PONG".equals(f.type())) {
                keepAliveStatus = "ok";
                lastPingOkAtMs = System.currentTimeMillis();
                lastPingRttMs  = rtt;
                log("PONG session=" + sessionId + " rtt=" + rtt + "ms");
            } else {
                keepAliveStatus = f.get("code");
                log("PING_ERR " + f.get("code"));
            }
        } catch (Exception e) {
            keepAliveStatus = "failed";
            log("PING_ERR " + e.getMessage());
        }
    }

    private void logoutSession() {
        if (!hasActiveSession()) return;
        String sid = sessionId;
        try {
            MessageFrame f = RemoteGateway.logout(host.get(), portField.get(), sid);
            log("LOGOUT " + ("BYE".equals(f.type()) ? "OK" : f.get("code")) + " session=" + sid);
        } catch (Exception e) {
            log("LOGOUT_ERR " + e.getMessage());
        } finally {
            clearSessionState("Logged out");
        }
    }

    private void gracefulDisconnect(String reason) {
        if (!hasActiveSession()) return;
        String sid = sessionId;
        try {
            RemoteGateway.logout(host.get(), portField.get(), sid);
            log("DISCONNECT session=" + sid + " reason=" + reason);
        } catch (Exception e) {
            log("DISCONNECT_ERR " + e.getMessage());
        } finally {
            clearSessionState("Disconnected");
        }
    }

    // ── State helpers ────────────────────────────────────────────────────────

    private boolean hasAuthToken()      { return authToken != null && !authToken.equals("-"); }
    private boolean hasSelectedCharacter() { return selectedCharId != null && !selectedCharId.equals("-"); }
    private boolean hasActiveSession()  {
        return sessionId != null && !sessionId.equals("-") && mode == RuntimeMode.REMOTE;
    }

    private void clearRemoteAuth() {
        authToken = "-";
        accountId = "-";
        selectedCharId = "-";
        selectedCharName = "-";
        createFormVisible = false;
        characters.clear();
        clearSessionState("Signed out");
    }

    private void clearSessionState(String newStatus) {
        status = newStatus;
        sessionId = "-";
        keepAliveStatus = hasAuthToken() ? "waiting for character select" : "disconnected";
        lastPingAtMs   = 0L;
        lastPingOkAtMs = 0L;
        lastPingRttMs  = -1L;
    }

    // ── Log / debug window ───────────────────────────────────────────────────

    private void drawLogWindow() {
        ImGui.setNextWindowPos(800, 20, ImGuiCond.Once);
        ImGui.setNextWindowSize(460, 680, ImGuiCond.Once);
        ImGui.begin("Debug Log");
        ImGui.checkbox("Auto scroll", autoScroll);
        ImGui.sameLine();
        if (ImGui.button("Clear")) logs.clear();
        ImGui.separator();
        ImGui.beginChild("log-scroll");
        for (String line : logs) ImGui.textUnformatted(line);
        if (autoScroll.get()) ImGui.setScrollHereY(1f);
        ImGui.endChild();
        ImGui.end();
    }

    private void log(String msg) {
        logs.add("[" + LocalTime.now().format(TIME_FMT) + "] " + msg);
    }

    private String formatTime(long epochMs) {
        return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime().format(TIME_FMT);
    }

    private int parseInt(String v, int fallback) {
        if (v == null) return fallback;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return fallback; }
    }

    // ── Data types ───────────────────────────────────────────────────────────

    private record RemoteCharacter(
        String characterId, String name,
        int race, String raceName,
        int size, int face,
        int mainJob, String jobName,
        int nation
    ) {
        String nationName() {
            return switch (nation) { case 0 -> "Sandy"; case 1 -> "Bastok"; case 2 -> "Windurst"; default -> "?"; };
        }
    }
}
