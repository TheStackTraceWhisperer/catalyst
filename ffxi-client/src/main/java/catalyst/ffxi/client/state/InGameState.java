package catalyst.ffxi.client.state;

import catalyst.ffxi.client.network.KeepAliveService;
import catalyst.ffxi.client.network.QuicGatewayService;
import catalyst.ffxi.client.ui.DebugLogPanel;
import catalyst.ffxi.client.ui.InGamePanel;
import catalyst.ffxi.engine.services.state.ApplicationState;
import catalyst.ffxi.engine.services.state.ApplicationStateService;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.opengl.GL11;

@Slf4j
@Prototype
@RequiredArgsConstructor
public class InGameState implements ApplicationState {

    private final InGamePanel panel;
    private final DebugLogPanel debugLog;
    private final KeepAliveService keepAlive;
    private final QuicGatewayService gateway;
    private final ApplicationStateService stateService;
    private final BeanProvider<UnauthenticatedState> unauthProvider;

    private String host, sessionId, characterName;
    private int    port;

    public void init(String host, int port, String sessionId, String characterName, int zoneId) {
        this.host = host; this.port = port; this.sessionId = sessionId; this.characterName = characterName;
        panel.setContext(characterName, sessionId);
        debugLog.log("Entered zone " + zoneId + " as " + characterName);
    }

    @Override
    public void onEnter() {
        keepAlive.start(host, port, sessionId);
    }

    @Override
    public void onUpdate(float dt) {
        GL11.glClearColor(0.07f, 0.07f, 0.09f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        panel.updateKeepAlive(keepAlive.getStatus(), keepAlive.getLastRttMs());
        panel.render();
        debugLog.render();
        if (panel.isPingRequested())   { keepAlive.sendPing(); }
        if (panel.isLogoutRequested()) doLogout();
        panel.clearIntents();
    }

    @Override
    public void onExit() {
        keepAlive.stop();
        tryLogout();
    }

    private void doLogout() {
        keepAlive.stop();
        tryLogout();
        stateService.changeState(unauthProvider::get);
    }

    private void tryLogout() {
        try {
            gateway.logout(host, port, sessionId);
            debugLog.log("LOGOUT_OK session=" + sessionId);
        } catch (Exception e) {
            debugLog.log("LOGOUT_ERR " + e.getMessage());
        }
    }
}
