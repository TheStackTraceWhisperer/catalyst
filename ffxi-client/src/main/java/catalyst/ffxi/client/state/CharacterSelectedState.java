package catalyst.ffxi.client.state;

import catalyst.ffxi.client.network.QuicGatewayService;
import catalyst.ffxi.client.ui.CharacterPanel;
import catalyst.ffxi.client.ui.DebugLogPanel;
import catalyst.ffxi.common.net.MessageFrame;
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
public class CharacterSelectedState implements ApplicationState {

    private final CharacterPanel panel;
    private final DebugLogPanel debugLog;
    private final QuicGatewayService gateway;
    private final ApplicationStateService stateService;
    private final BeanProvider<UnauthenticatedState> unauthProvider;
    private final BeanProvider<InGameState> inGameProvider;

    private String host, authToken, characterId, characterName;
    private int    port, currentZoneId;

    public void init(String host, int port, String authToken, String characterId, String characterName, int currentZoneId) {
        this.host = host; this.port = port; this.authToken = authToken;
        this.characterId = characterId; this.characterName = characterName; this.currentZoneId = currentZoneId;
    }

    @Override
    public void onEnter() {
        panel.setSelectedCharacter(characterId, characterName);
        panel.setStatus("Selected: " + characterName + " — click Play to enter zone " + currentZoneId);
        debugLog.log("CHAR_SELECT_OK " + characterName + " zone=" + currentZoneId);
    }

    @Override
    public void onUpdate(float dt) {
        GL11.glClearColor(0.07f, 0.07f, 0.09f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        panel.render();
        debugLog.render();
        if (panel.isSignOutRequested()) stateService.changeState(unauthProvider::get);
        if (panel.isPlayRequested())    doPlay();
        panel.clearIntents();
    }

    @Override
    public void onExit() {}

    private void doPlay() {
        try {
            MessageFrame resp = gateway.play(host, port, authToken, characterId);
            if (!"PLAY_OK".equals(resp.type())) { debugLog.log("PLAY_ERR " + resp.get("code")); return; }
            String sessionId = resp.get("sessionId");
            int zoneId = resp.getInt("zoneId", currentZoneId);
            int pop    = resp.getInt("playersInZone", 0);
            debugLog.log("PLAY_OK session=" + sessionId + " zone=" + zoneId + " players=" + pop);
            InGameState next = inGameProvider.get();
            next.init(host, port, sessionId, characterName, zoneId);
            stateService.changeState(() -> next);
        } catch (Exception e) { debugLog.log("PLAY_ERR " + e.getMessage()); }
    }
}
