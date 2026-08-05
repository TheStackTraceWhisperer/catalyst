package catalyst.client.application.state;

import catalyst.client.network.QuicGatewayService;
import catalyst.client.network.QuicGatewayService.CharacterSummary;
import catalyst.client.application.ui.CharacterPanel;
import catalyst.client.application.ui.CharacterPanel.CharRow;
import catalyst.client.application.ui.DebugLogPanel;
import catalyst.common.network.ResponseCode;
import catalyst.common.dto.*;
import catalyst.client.engine.services.state.ApplicationState;
import catalyst.client.engine.services.state.ApplicationStateService;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.opengl.GL11;

import java.util.List;

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

    private String host, authToken, accountId, characterId, characterName;
    private int    port, currentZoneId;

    public void init(String host, int port, String authToken, String accountId, String characterId, String characterName, int currentZoneId) {
        this.host = host; this.port = port; this.authToken = authToken;
        this.accountId = accountId;
        this.characterId = characterId; this.characterName = characterName; this.currentZoneId = currentZoneId;
    }

    @Override
    public void onEnter() {
        refreshCharacters();
        panel.setSelectedCharacter(characterId, characterName);
        updateSelectedStatus();
        debugLog.log("CHAR_SELECT_OK " + characterName + " zone=" + currentZoneId);
    }

    @Override
    public void onUpdate(float dt) {
        GL11.glClearColor(0.07f, 0.07f, 0.09f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        panel.render();
        debugLog.render();
        processIntents();
        panel.clearIntents();
    }

    @Override
    public void onExit() {}

    private void processIntents() {
        if (panel.isRefreshRequested()) refreshCharacters();
        if (panel.isSignOutRequested()) stateService.changeState(unauthProvider::get);
        if (panel.getSelectCharacterId() != null) doSelect(panel.getSelectCharacterId());
        if (panel.getDeleteCharacterId() != null) doDelete(panel.getDeleteCharacterId());
        if (panel.isCreateSubmitted()) doCreate();
        if (panel.isPlayRequested()) doPlay();
    }

    private void refreshCharacters() {
        try {
            List<CharacterSummary> summaries = gateway.listCharacterSummaries(host, port, authToken);
            List<CharRow> rows = summaries.stream()
                .map(c -> new CharRow(c.id(), c.name(), c.raceName(), c.size(), c.face(), c.jobName(), c.nationName()))
                .toList();
            panel.setCharacters(rows);
            updateSelectedStatus();
            debugLog.log("CHAR_LIST_OK count=" + rows.size());
        } catch (Exception e) {
            debugLog.log("CHAR_LIST_ERR " + e.getMessage());
        }
    }

    private void doSelect(String charId) {
        try {
            CharSelectResponse resp = gateway.selectCharacter(host, port, authToken, charId);
            if (resp.getCode() != ResponseCode.OK) { debugLog.log("CHAR_SELECT_ERR " + resp.getCode()); return; }
            characterId = charId;
            characterName = resp.getCharacterName();
            currentZoneId = resp.getCurrentZoneId();
            panel.setSelectedCharacter(characterId, characterName);
            updateSelectedStatus();
            debugLog.log("CHAR_SELECT_OK " + characterName + " zone=" + currentZoneId);
        } catch (Exception e) {
            debugLog.log("CHAR_SELECT_ERR " + e.getMessage());
        }
    }

    private void doDelete(String charId) {
        try {
            CharDeleteResponse resp = gateway.deleteCharacter(host, port, authToken, charId);
            if (resp.getCode() == ResponseCode.OK) {
                debugLog.log("CHAR_DELETE_OK id=" + charId);
                refreshCharacters();
            } else {
                debugLog.log("CHAR_DELETE_ERR " + resp.getCode());
            }
        } catch (Exception e) {
            debugLog.log("CHAR_DELETE_ERR " + e.getMessage());
        }
    }

    private void doCreate( ) {
        try {
            CharCreateResponse resp = gateway.createCharacter(host, port, authToken, panel.getNewName(),
                panel.getRaceId(), panel.getSizeId(), panel.getFaceId(), panel.getJobId(),
                Integer.toString(panel.getNationId()));
            if (resp.getCode() == ResponseCode.OK) {
                debugLog.log("CHAR_CREATE_OK id=" + resp.getCharacterId() + " name=" + resp.getName());
                panel.hideCreateForm();
                refreshCharacters();
            } else {
                debugLog.log("CHAR_CREATE_ERR " + resp.getCode() + " " + resp.getMessage());
            }
        } catch (Exception e) {
            debugLog.log("CHAR_CREATE_ERR " + e.getMessage());
        }
    }

    private void updateSelectedStatus() {
        panel.setStatus("Selected: " + characterName + " — click Play to enter zone " + currentZoneId);
    }

    private void doPlay() {
        try {
            PlayResponse resp = gateway.play(host, port, authToken, characterId);
            if (resp.getCode() != ResponseCode.OK) { debugLog.log("PLAY_ERR " + resp.getCode()); return; }
            String sessionId = resp.getSessionId();
            int zoneId = resp.getZoneId();
            int pop    = resp.getPlayersInZone();
            long keepaliveIntervalMs = resp.getKeepaliveIntervalMs();
            debugLog.log("PLAY_OK session=" + sessionId + " zone=" + zoneId + " players=" + pop);
            InGameState next = inGameProvider.get();
            next.init(host, port, authToken, accountId, sessionId, characterId, characterName, zoneId, keepaliveIntervalMs);
            stateService.changeState(() -> next);
        } catch (Exception e) { debugLog.log("PLAY_ERR " + e.getMessage()); }
    }
}
