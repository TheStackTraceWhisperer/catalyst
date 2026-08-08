package catalyst.client.application.state;

import catalyst.client.network.KeepAliveService;
import catalyst.client.network.QuicGatewayService;
import catalyst.client.application.ui.DebugLogPanel;
import catalyst.client.application.ui.InGamePanel;
import catalyst.common.dto.*;
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
public class InGameState implements ApplicationState {

    private final InGamePanel panel;
    private final DebugLogPanel debugLog;
    private final KeepAliveService keepAlive;
    private final QuicGatewayService gateway;
    private final ApplicationStateService stateService;
    private final BeanProvider<UnauthenticatedState> unauthProvider;
    private final BeanProvider<CharacterSelectedState> selectedProvider;

    private String host, authToken, accountId, sessionId, characterId, characterName;
    private int    port, currentZoneId;
    private long   keepaliveIntervalMs;
    private boolean sessionClosed;

    public void init(String host, int port, String authToken, String accountId,
                     String sessionId, String characterId, String characterName, int zoneId, long keepaliveIntervalMs) {
        this.host = host;
        this.port = port;
        this.authToken = authToken;
        this.accountId = accountId;
        this.sessionId = sessionId;
        this.characterId = characterId;
        this.characterName = characterName;
        this.currentZoneId = zoneId;
        this.keepaliveIntervalMs = keepaliveIntervalMs;
        this.sessionClosed = false;
        panel.setContext(characterName, sessionId);
        debugLog.log("Entered zone " + zoneId + " as " + characterName);
    }

    @Override
    public void onEnter() {
        keepAlive.start(host, port, sessionId, keepaliveIntervalMs);
    }

    @Override
    public void onUpdate(float dt) {
        GL11.glClearColor(0.07f, 0.07f, 0.09f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        panel.updateKeepAlive(keepAlive.getStatus(), keepAlive.getLastRttMs());
        panel.render();
        debugLog.render();
        if (panel.isPingRequested())   { keepAlive.sendPing(); }
        if (panel.isCharacterSelectRequested()) doCharacterSelect();
        if (panel.isLogoutRequested()) doLogout();
        panel.clearIntents();
    }

    @Override
    public void onExit() {
        keepAlive.stop();
        closeSession();
    }

    private void doLogout() {
        closeSession();
        stateService.changeState(unauthProvider::get);
    }

    private void doCharacterSelect() {
        closeSession();
        CharacterSelectedState next = selectedProvider.get();
        next.init(host, port, authToken, accountId, characterId, characterName, currentZoneId);
        stateService.changeState(() -> next);
    }

    private void closeSession() {
        keepAlive.stop();
        tryLogout();
    }

    private void tryLogout() {
        if (sessionClosed) return;
        sessionClosed = true;
        try {
            LogoutResponse resp = gateway.request(host, port, new LogoutRequest(sessionId), LogoutResponse.class);
            debugLog.log("LOGOUT_OK session=" + resp.sessionId());
        } catch (Exception e) {
            debugLog.log("LOGOUT_ERR " + e.getMessage());
        }
    }
}
