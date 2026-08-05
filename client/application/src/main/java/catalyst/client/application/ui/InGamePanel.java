package catalyst.client.application.ui;

import imgui.ImGui;
import imgui.flag.ImGuiCond;
import io.micronaut.context.annotation.Prototype;
import lombok.Getter;

@Prototype
public class InGamePanel {

    @Getter private boolean pingRequested;
    @Getter private boolean logoutRequested;
    @Getter private boolean characterSelectRequested;

    private String characterName = "";
    private String status        = "";
    private String keepAliveStatus = "connected";
    private long   lastRttMs    = -1;

    public void setContext(String characterName, String sessionId) {
        this.characterName = characterName;
        this.status = "Playing as " + characterName + " (session=" + sessionId + ")";
    }

    public void updateKeepAlive(String ks, long rtt) {
        this.keepAliveStatus = ks;
        this.lastRttMs = rtt;
    }

    public void render() {
        ImGui.setNextWindowPos(20, 20, ImGuiCond.Once);
        ImGui.setNextWindowSize(500, 200, ImGuiCond.Once);
        ImGui.begin("In Game");
        ImGui.text(status);
        ImGui.text("KeepAlive: " + keepAliveStatus + (lastRttMs >= 0 ? "  RTT=" + lastRttMs + "ms" : ""));
        ImGui.separator();
        if (ImGui.button("Ping Now")) pingRequested   = true;
        ImGui.sameLine();
        if (ImGui.button("Character Select")) characterSelectRequested = true;
        ImGui.sameLine();
        if (ImGui.button("Logout"))    logoutRequested = true;
        ImGui.end();
    }

    public void clearIntents() {
        pingRequested = false;
        characterSelectRequested = false;
        logoutRequested = false;
    }
}
