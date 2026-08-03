package catalyst.ffxi.client.state;

import catalyst.ffxi.client.network.QuicGatewayService;
import catalyst.ffxi.client.ui.CharacterPanel;
import catalyst.ffxi.client.ui.CharacterPanel.CharRow;
import catalyst.ffxi.client.ui.DebugLogPanel;
import catalyst.ffxi.common.net.MessageFrame;
import catalyst.ffxi.engine.services.state.ApplicationState;
import catalyst.ffxi.engine.services.state.ApplicationStateService;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
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
            MessageFrame resp = gateway.listCharacters(host, port, authToken);
            if (!"CHAR_LIST_OK".equals(resp.type())) { debugLog.log("CHAR_LIST_ERR " + resp.get("code")); return; }
            int count = resp.getInt("count", 0);
            List<CharRow> rows = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int nation = resp.getInt("char" + i + "_nation", 0);
                rows.add(new CharRow(resp.get("char" + i + "_id"), resp.get("char" + i + "_name"),
                    resp.get("char" + i + "_raceName"), resp.getInt("char" + i + "_size", 1),
                    resp.getInt("char" + i + "_face", 0), resp.get("char" + i + "_jobName"),
                    switch (nation) { case 0 -> "Sandy"; case 1 -> "Bastok"; default -> "Windurst"; }));
            }
            panel.setCharacters(rows);
            panel.setStatus(rows.size() + " character(s)");
            debugLog.log("CHAR_LIST_OK count=" + rows.size());
        } catch (Exception e) { debugLog.log("CHAR_LIST_ERR " + e.getMessage()); }
    }

    private void doSelect(String charId) {
        try {
            MessageFrame resp = gateway.selectCharacter(host, port, authToken, charId);
            if (!"CHAR_SELECT_OK".equals(resp.type())) { debugLog.log("CHAR_SELECT_ERR " + resp.get("code")); return; }
            String charName = resp.get("characterName");
            panel.setSelectedCharacter(charId, charName);
            CharacterSelectedState next = selectedProvider.get();
            next.init(host, port, authToken, charId, charName, resp.getInt("currentZoneId", 0));
            stateService.changeState(() -> next);
        } catch (Exception e) { debugLog.log("CHAR_SELECT_ERR " + e.getMessage()); }
    }

    private void doDelete(String charId) {
        try {
            MessageFrame resp = gateway.deleteCharacter(host, port, authToken, charId);
            if ("CHAR_DELETE_OK".equals(resp.type())) { debugLog.log("CHAR_DELETE_OK id=" + charId); refreshCharacters(); }
            else debugLog.log("CHAR_DELETE_ERR " + resp.get("code"));
        } catch (Exception e) { debugLog.log("CHAR_DELETE_ERR " + e.getMessage()); }
    }

    private void doCreate() {
        try {
            MessageFrame resp = gateway.createCharacter(host, port, authToken, panel.getNewName(),
                panel.getRaceId(), panel.getSizeId(), panel.getFaceId(), panel.getJobId(),
                Integer.toString(panel.getNationId()));
            if ("CHAR_CREATE_OK".equals(resp.type())) {
                debugLog.log("CHAR_CREATE_OK id=" + resp.get("characterId") + " name=" + resp.get("name"));
                panel.hideCreateForm();
                refreshCharacters();
            } else debugLog.log("CHAR_CREATE_ERR " + resp.get("code") + " " + resp.get("message"));
        } catch (Exception e) { debugLog.log("CHAR_CREATE_ERR " + e.getMessage()); }
    }
}
