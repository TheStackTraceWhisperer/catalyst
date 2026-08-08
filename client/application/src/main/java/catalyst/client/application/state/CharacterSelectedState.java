package catalyst.client.application.state;

import catalyst.client.application.ui.CharacterPanel;
import catalyst.client.application.ui.CharacterPanel.CharRow;
import catalyst.client.application.ui.DebugLogPanel;
import catalyst.client.engine.services.state.ApplicationStateService;
import catalyst.client.engine.services.state.ApplicationState;
import catalyst.common.network.ResponseCode;
import catalyst.client.network.QuicGatewayService;
import catalyst.common.dto.*;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.lwjgl.opengl.GL11;

import java.util.List;

@Prototype
@RequiredArgsConstructor
public class CharacterSelectedState implements ApplicationState {

    private final CharacterPanel panel;
    private final DebugLogPanel debugLog;
    private final QuicGatewayService gateway;
    private final ApplicationStateService stateService;
    private final BeanProvider<UnauthenticatedState> unauthProvider;
    private final BeanProvider<InGameState> inGameProvider;

    private String authToken, accountId, characterId, characterName;
    private int    currentZoneId;

    public void init(String authToken, String accountId, String characterId, String characterName, int currentZoneId) {
        this.authToken = authToken;
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
            CharListResponse resp = gateway.request(new CharListRequest(authToken), CharListResponse.class);
            if (resp.code() != ResponseCode.OK) {
                throw new Exception("CHAR_LIST_ERR " + resp.code());
            }
            List<CharRow> rows = resp.characters().stream()
                .map(c -> {
                    String nationStr = switch (c.nation()) {
                        case 0 -> "Sandy";
                        case 1 -> "Bastok";
                        default -> "Windurst";
                    };
                    return new CharRow(c.id(), c.name(), c.raceName(), c.size(), c.face(), c.jobName(), nationStr);
                })
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
            long characterIdVal = Long.parseLong(charId);
            CharSelectResponse resp = gateway.request(new CharSelectRequest(authToken, characterIdVal), CharSelectResponse.class);
            if (resp.code() != ResponseCode.OK) { debugLog.log("CHAR_SELECT_ERR " + resp.code()); return; }
            characterId = charId;
            characterName = resp.characterName();
            currentZoneId = resp.currentZoneId();
            panel.setSelectedCharacter(characterId, characterName);
            updateSelectedStatus();
            debugLog.log("CHAR_SELECT_OK " + characterName + " zone=" + currentZoneId);
        } catch (Exception e) {
            debugLog.log("CHAR_SELECT_ERR " + e.getMessage());
        }
    }

    private void doDelete(String charId) {
        try {
            long characterIdVal = Long.parseLong(charId);
            CharDeleteResponse resp = gateway.request(new CharDeleteRequest(authToken, characterIdVal), CharDeleteResponse.class);
            if (resp.code() == ResponseCode.OK) {
                debugLog.log("CHAR_DELETE_OK id=" + charId);
                refreshCharacters();
            } else {
                debugLog.log("CHAR_DELETE_ERR " + resp.code());
            }
        } catch (Exception e) {
            debugLog.log("CHAR_DELETE_ERR " + e.getMessage());
        }
    }

    private void doCreate() {
        try {
            CharCreateResponse resp = gateway.request(
                new CharCreateRequest(authToken, panel.getNewName(), panel.getRaceId(), panel.getSizeId(),
                    panel.getFaceId(), panel.getJobId(), Integer.toString(panel.getNationId())),
                CharCreateResponse.class);
            if (resp.code() == ResponseCode.OK) {
                debugLog.log("CHAR_CREATE_OK id=" + resp.characterId() + " name=" + resp.name());
                panel.hideCreateForm();
                refreshCharacters();
            } else {
                debugLog.log("CHAR_CREATE_ERR " + resp.code() + " " + resp.message());
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
            long characterIdVal = Long.parseLong(characterId);
            PlayResponse resp = gateway.request(new PlayRequest(authToken, characterIdVal), PlayResponse.class);
            if (resp.code() != ResponseCode.OK) { debugLog.log("PLAY_ERR " + resp.code()); return; }
            String sessionId = resp.sessionId();
            int zoneId = resp.zoneId();
            int pop    = resp.playersInZone();
            long keepaliveIntervalMs = resp.keepaliveIntervalMs();
            debugLog.log("PLAY_OK session=" + sessionId + " zone=" + zoneId + " players=" + pop);
            InGameState next = inGameProvider.get();
            next.init(authToken, accountId, sessionId, characterId, characterName, zoneId, keepaliveIntervalMs);
            stateService.changeState(() -> next);
        } catch (Exception e) { debugLog.log("PLAY_ERR " + e.getMessage()); }
    }
}
