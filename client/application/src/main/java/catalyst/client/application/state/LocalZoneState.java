package catalyst.client.application.state;

import catalyst.client.application.ui.DebugLogPanel;
import catalyst.client.application.ui.InGamePanel;
import catalyst.client.engine.services.state.ApplicationState;
import catalyst.client.engine.services.state.ApplicationStateService;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.opengl.GL11;

@Slf4j
@Prototype
@RequiredArgsConstructor
public class LocalZoneState implements ApplicationState {

    private final InGamePanel panel;
    private final DebugLogPanel debugLog;
    private final ApplicationStateService stateService;
    private final BeanProvider<UnauthenticatedState> unauthProvider;

    @Override
    public void onEnter() {
        panel.setContext("LocalDev", "LOCAL");
        debugLog.log("LOCAL MODE — zone=230 char=LocalDev (no server)");
    }

    @Override
    public void onUpdate(float dt) {
        GL11.glClearColor(0.05f, 0.07f, 0.05f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        panel.render();
        debugLog.render();
        if (panel.isLogoutRequested()) stateService.changeState(unauthProvider::get);
        panel.clearIntents();
    }

    @Override
    public void onExit() {
        debugLog.log("Exiting local mode");
    }
}
