package catalyst.ffxi.client.ui;

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
    @Getter private boolean localModeRequested;

    public void render() {
        ImGui.setNextWindowPos(20, 20, ImGuiCond.Once);
        ImGui.setNextWindowSize(500, 200, ImGuiCond.Once);
        ImGui.begin("Login");
        ImGui.inputText("Username", username);
        ImGui.inputText("Password##pw", password);
        if (ImGui.button("Login (Remote)"))  loginRequested     = true;
        ImGui.sameLine();
        if (ImGui.button("Local Mode"))       localModeRequested = true;
        ImGui.end();
    }

    public String getUsername() { return username.get(); }
    public String getPassword() { return password.get(); }
    public void clearIntents() { loginRequested = false; localModeRequested = false; }
}
