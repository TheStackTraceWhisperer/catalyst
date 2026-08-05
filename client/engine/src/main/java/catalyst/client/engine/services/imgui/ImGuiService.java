package catalyst.client.engine.services.imgui;

import catalyst.client.engine.IService;
import catalyst.client.engine.services.window.WindowService;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ImGuiService implements IService {

    private final WindowService windowService;

    private ImGuiImplGlfw implGlfw;
    private ImGuiImplGl3  implGl3;

    @Override
    public int executionOrder() { return Integer.MIN_VALUE + 2; }

    @Override
    public void start() {
        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);

        implGlfw = new ImGuiImplGlfw();
        implGl3  = new ImGuiImplGl3();
        implGlfw.init(windowService.getHandle(), true);
        implGl3.init("#version 460");
        log.info("Dear ImGui initialized");
    }

    @Override
    public void update() {
        implGlfw.newFrame();
        ImGui.newFrame();
    }

    @Override
    public void postUpdate() {
        ImGui.render();
        implGl3.renderDrawData(ImGui.getDrawData());
    }

    @Override
    public void stop() {
        if (implGl3 != null)  { implGl3.dispose();  implGl3  = null; }
        if (implGlfw != null) { implGlfw.dispose(); implGlfw = null; }
        ImGui.destroyContext();
        log.info("Dear ImGui disposed");
    }
}
