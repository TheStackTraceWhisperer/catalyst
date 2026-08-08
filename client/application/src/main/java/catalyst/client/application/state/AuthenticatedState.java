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
public class AuthenticatedState implements ApplicationState {

    private final CharacterPanel panel;
    private final DebugLogPanel debugLog;
    private final QuicGatewayService gateway;
    private final ApplicationStateService stateService;
    private final BeanProvider<UnauthenticatedState> unauthProvider;
    private final BeanProvider<CharacterSelectedState> selectedProvider;

    private String authToken, accountId;

    public void init(String authToken, String accountId) {
        this.authToken = authToken; this.accountId = accountId;
    }

    @Override
    public void onEnter() {
        panel.setStatus("Authenticated — select or create a character");
        refreshCharacters();
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
        if (panel.isSignOutRequested()) {
            debugLog.log("Signed out");
            stateService.changeState(unauthProvider::get);
        }
        if (panel.getSelectCharacterId() != null) doSelect(panel.getSelectCharacterId());
        if (panel.getDeleteCharacterId() != null) doDelete(panel.getDeleteCharacterId());
        if (panel.isCreateSubmitted())             doCreate();
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
            panel.setStatus(rows.size() + " character(s)");
            debugLog.log("CHAR_LIST_OK count=" + rows.size());
        } catch (Exception e) {
            debugLog.log("CHAR_LIST_ERR " + e.getMessage());
        }
    }

    private void doSelect(String charId) {
        try {
            long characterId = Long.parseLong(charId);
            CharSelectResponse resp = gateway.request(new CharSelectRequest(authToken, characterId), CharSelectResponse.class);
            if (resp.code() != ResponseCode.OK) { debugLog.log("CHAR_SELECT_ERR " + resp.code()); return; }
            String charName = resp.characterName();
            panel.setSelectedCharacter(charId, charName);
            CharacterSelectedState next = selectedProvider.get();
            next.init(authToken, accountId, charId, charName, resp.currentZoneId());
            stateService.changeState(() -> next);
        } catch (Exception e) { debugLog.log("CHAR_SELECT_ERR " + e.getMessage()); }
    }

    private void doDelete(String charId) {
        try {
            long characterId = Long.parseLong(charId);
            CharDeleteResponse resp = gateway.request(new CharDeleteRequest(authToken, characterId), CharDeleteResponse.class);
            if (resp.code() == ResponseCode.OK) { 
                debugLog.log("CHAR_DELETE_OK id=" + charId); 
                refreshCharacters(); 
            } else {
                debugLog.log("CHAR_DELETE_ERR " + resp.code());
            }
        } catch (Exception e) { debugLog.log("CHAR_DELETE_ERR " + e.getMessage()); }
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
        } catch (Exception e) { debugLog.log("CHAR_CREATE_ERR " + e.getMessage()); }
    }
}
