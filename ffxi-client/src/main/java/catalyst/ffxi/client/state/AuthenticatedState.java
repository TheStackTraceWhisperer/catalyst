package catalyst.ffxi.client.state;

import catalyst.ffxi.client.network.QuicGatewayService;
import catalyst.ffxi.client.network.QuicGatewayService.CharacterSummary;
import catalyst.ffxi.client.ui.CharacterPanel;
import catalyst.ffxi.client.ui.CharacterPanel.CharRow;
import catalyst.ffxi.client.ui.DebugLogPanel;
import catalyst.ffxi.common.net.MessageFrame;
import catalyst.ffxi.common.net.dto.*;
import catalyst.ffxi.engine.services.state.ApplicationState;
import catalyst.ffxi.engine.services.state.ApplicationStateService;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.opengl.GL11;

import java.util.List;

@Slf4j
@Prototype
@RequiredArgsConstructor
public class AuthenticatedState implements ApplicationState {

    private final CharacterPanel panel;
    private final DebugLogPanel debugLog;
    private final QuicGatewayService gateway;
    private final ApplicationStateService stateService;
    private final BeanProvider<UnauthenticatedState> unauthProvider;
    private final BeanProvider<CharacterSelectedState> selectedProvider;

    private String host, authToken, accountId;
    private int    port;

    public void init(String host, int port, String authToken, String accountId) {
        this.host = host; this.port = port; this.authToken = authToken; this.accountId = accountId;
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
            List<CharacterSummary> summaries = gateway.listCharacterSummaries(host, port, authToken);
            List<CharRow> rows = summaries.stream()
                .map(c -> new CharRow(c.id(), c.name(), c.raceName(), c.size(), c.face(), c.jobName(), c.nationName()))
                .toList();
            panel.setCharacters(rows);
            panel.setStatus(rows.size() + " character(s)");
            debugLog.log("CHAR_LIST_OK count=" + rows.size());
        } catch (Exception e) { debugLog.log("CHAR_LIST_ERR " + e.getMessage()); }
    }

    private void doSelect(String charId) {
        try {
            MessageFrame respFrame = gateway.selectCharacter(host, port, authToken, charId);
            if (!"CHAR_SELECT_OK".equals(respFrame.type())) { debugLog.log("CHAR_SELECT_ERR " + respFrame.get("code")); return; }
            CharSelectResponse resp = ProtocolMapper.toCharSelectResponse(respFrame);
            String charName = resp.getCharacterName();
            panel.setSelectedCharacter(charId, charName);
            CharacterSelectedState next = selectedProvider.get();
            next.init(host, port, authToken, accountId, charId, charName, resp.getCurrentZoneId());
            stateService.changeState(() -> next);
        } catch (Exception e) { debugLog.log("CHAR_SELECT_ERR " + e.getMessage()); }
    }

    private void doDelete(String charId) {
        try {
            MessageFrame respFrame = gateway.deleteCharacter(host, port, authToken, charId);
            if ("CHAR_DELETE_OK".equals(respFrame.type())) { 
                debugLog.log("CHAR_DELETE_OK id=" + charId); 
                refreshCharacters(); 
            } else {
                debugLog.log("CHAR_DELETE_ERR " + respFrame.get("code"));
            }
        } catch (Exception e) { debugLog.log("CHAR_DELETE_ERR " + e.getMessage()); }
    }

    private void doCreate() {
        try {
            MessageFrame respFrame = gateway.createCharacter(host, port, authToken, panel.getNewName(),
                panel.getRaceId(), panel.getSizeId(), panel.getFaceId(), panel.getJobId(),
                Integer.toString(panel.getNationId()));
            if ("CHAR_CREATE_OK".equals(respFrame.type())) {
                CharCreateResponse resp = ProtocolMapper.toCharCreateResponse(respFrame);
                debugLog.log("CHAR_CREATE_OK id=" + resp.getCharacterId() + " name=" + resp.getName());
                panel.hideCreateForm();
                refreshCharacters();
            } else {
                debugLog.log("CHAR_CREATE_ERR " + respFrame.get("code") + " " + respFrame.get("message"));
            }
        } catch (Exception e) { debugLog.log("CHAR_CREATE_ERR " + e.getMessage()); }
    }
}
