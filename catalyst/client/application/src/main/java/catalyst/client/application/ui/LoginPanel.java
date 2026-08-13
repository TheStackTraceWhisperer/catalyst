package catalyst.client.application.ui;

import catalyst.client.application.ClientState;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.type.ImString;
import io.micronaut.context.annotation.Prototype;
import lombok.Getter;

@Prototype
public class LoginPanel {

    private final ImString username = new ImString("dev", 64);
    private final ImString password = new ImString("dev", 64);
    @Getter private boolean loginRequested;

    public void render(ClientState clientState) {
        ImGui.setNextWindowPos(20, 20, ImGuiCond.Once);
        ImGui.setNextWindowSize(500, 220, ImGuiCond.Once);
        ImGui.begin("Login");

        // Display errors or status from ClientState
        if (clientState.getLastErrorMessage() != null) {
            ImGui.textColored(1.0f, 0.2f, 0.2f, 1.0f, "Error: " + clientState.getLastErrorMessage());
            ImGui.separator();
        }

        ImGui.inputText("Username", username);
        ImGui.inputText("Password##pw", password);

        if (clientState.getPhase() == ClientState.AppPhase.AUTHENTICATING) {
            ImGui.textDisabled("Connecting & Authenticating...");
        } else {
            if (ImGui.button("Login")) loginRequested = true;
        }

        ImGui.end();
    }

    public String getUsername() { return username.get(); }
    public String getPassword() { return password.get(); }
    public void clearIntents() { loginRequested = false; }
}