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
import java.time.LocalTime;
import java.time.Instant;
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

    private final ImString host = new ImString("127.0.0.1", 128);
    private final ImInt port = new ImInt(35555);
    private final ImString username = new ImString("dev", 64);
    private final ImString password = new ImString("dev", 64);
    private final ImString character = new ImString("DevCharacter", 64);
    private final ImBoolean autoScroll = new ImBoolean(true);

    private final List<String> logs = new ArrayList<>();
    private RuntimeMode mode = RuntimeMode.LOCAL;
    private String sessionId = "-";
    private String status = "Not connected";
    private String keepAliveStatus = "idle";
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
        ImGui.setNextWindowSize(560, 360, ImGuiCond.Once);
        ImGui.begin("Milestone 1 Login");

        if (ImGui.radioButton("Local Mode", mode == RuntimeMode.LOCAL)) {
            if (hasActiveRemoteSession()) {
                gracefulDisconnect("mode switch to local");
            }
            mode = RuntimeMode.LOCAL;
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
        ImGui.inputText("Host", host);
        ImGui.inputInt("Port", port);
        ImGui.inputText("Username", username);
        ImGui.inputText("Password", password);
        ImGui.inputText("Character", character);

        if (mode == RuntimeMode.LOCAL) {
            if (ImGui.button("Enter Local Zone")) {
                status = "Local zone loaded: zone=230 char=" + character.get();
                sessionId = "LOCAL-" + System.currentTimeMillis();
                log("Entered local-only zone bootstrap with preset character " + character.get());
            }
        } else {
            if (ImGui.button("Login Remote")) {
                doRemoteLogin();
            }
            ImGui.sameLine();
            if (ImGui.button("Ping now")) {
                sendKeepAlive();
            }
            ImGui.sameLine();
            if (ImGui.button("Logout")) {
                logoutRemoteSession();
            }
        }

        ImGui.separator();
        ImGui.text("Mode: " + mode);
        ImGui.text("Status: " + status);
        ImGui.text("Session: " + sessionId);
        ImGui.text("KeepAlive: " + keepAliveStatus);
        ImGui.text("Last RTT: " + (lastKeepAliveLatencyMs >= 0 ? lastKeepAliveLatencyMs + "ms" : "-"));
        ImGui.text("Last OK: " + (lastKeepAliveOkAtMs == 0 ? "-" : formatTime(lastKeepAliveOkAtMs)));
        ImGui.end();
    }

    private void doRemoteLogin() {
        try {
            MessageFrame frame = RemoteGateway.login(
                host.get(),
                port.get(),
                username.get(),
                password.get(),
                character.get()
            );
            if ("LOGIN_OK".equals(frame.type())) {
                status = frame.get("message");
                sessionId = frame.get("sessionId");
                keepAliveStatus = "connected";
                lastKeepAliveAtMs = 0L;
                log("LOGIN_OK session=" + sessionId + " character=" + frame.get("characterName")
                    + " zone=" + frame.get("currentZoneId"));
            } else {
                status = frame.get("code") + ": " + frame.get("message");
                sessionId = "-";
                keepAliveStatus = "disconnected";
                log("LOGIN_ERR code=" + frame.get("code") + " message=" + frame.get("message"));
            }
        } catch (Exception e) {
            status = "Connection error";
            sessionId = "-";
            keepAliveStatus = "disconnected";
            log("ERROR remote login failed: " + e.getMessage());
        }
    }

    private void maybeKeepAlive() {
        if (mode != RuntimeMode.REMOTE || sessionId.equals("-")) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastKeepAliveAtMs >= KEEPALIVE_INTERVAL_MS) {
            sendKeepAlive();
        }
    }

    private void sendKeepAlive() {
        if (mode != RuntimeMode.REMOTE || sessionId.equals("-")) {
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

    private boolean hasActiveRemoteSession() {
        return mode == RuntimeMode.REMOTE && !sessionId.equals("-");
    }

    private void clearRemoteSessionState(String newStatus) {
        status = newStatus;
        sessionId = "-";
        keepAliveStatus = "disconnected";
        lastKeepAliveAtMs = 0L;
        lastKeepAliveOkAtMs = 0L;
        lastKeepAliveLatencyMs = -1L;
    }

    private void drawLogWindow() {
        ImGui.setNextWindowPos(600, 20, ImGuiCond.Once);
        ImGui.setNextWindowSize(660, 680, ImGuiCond.Once);
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
}
