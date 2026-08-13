package catalyst.client.application.state;

import catalyst.client.application.ClientState;
import catalyst.client.application.ui.CharacterPanel;
import catalyst.client.application.ui.DebugLogPanel;
import catalyst.client.engine.services.state.ApplicationState;
import catalyst.client.engine.services.state.ApplicationStateService;
import catalyst.client.network.ClientTransportService;
import catalyst.common.dto.lobby.*;
import catalyst.common.network.DecodedPacket;
import catalyst.common.network.PacketType;
import catalyst.common.network.ResponseCode;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import lombok.RequiredArgsConstructor;
import org.lwjgl.opengl.GL11;

@Prototype
@RequiredArgsConstructor
public class AuthenticatedState implements ApplicationState {

    private final CharacterPanel panel;
    private final DebugLogPanel debugLog;
    private final ClientTransportService gateway;
    private final ApplicationStateService stateService;
    private final ClientState clientState;
    private final BeanProvider<UnauthenticatedState> unauthProvider;
    private final BeanProvider<CharacterSelectedState> selectedProvider;

    private long accountId;

    public void init(long accountId) {
        this.accountId = accountId;
    }

    @Override
    public void onEnter() {
        refreshCharacters();
    }

    @Override
    public void onUpdate(float dt) {
        GL11.glClearColor(0.07f, 0.07f, 0.09f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        panel.render(clientState);
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
            clientState.reset();
            stateService.changeState(unauthProvider::get);
        }
        if (panel.getSelectCharacterId() != null) doSelect(panel.getSelectCharacterId());
        if (panel.getDeleteCharacterId() != null) doDelete(panel.getDeleteCharacterId());
        if (panel.isCreateSubmitted())             doCreate();
    }

    private void refreshCharacters() {
        try {
            DecodedPacket packet = new DecodedPacket(PacketType.CHAR_LIST_REQUEST, new CharListRequest());
            CharListResponse resp = gateway.request(packet, CharListResponse.class);

            if (resp.code() != ResponseCode.OK) {
                throw new Exception("CHAR_LIST_ERR " + resp.code() + " " + resp.errorMessage());
            }

            clientState.onCharacterListReceived(resp.code(), resp.characters(), resp.errorMessage());
            debugLog.log("CHAR_LIST_OK count=" + resp.characters().size());
        } catch (Exception e) {
            debugLog.log("CHAR_LIST_ERR " + e.getMessage());
        }
    }

    private void doSelect(long characterId) {
        try {
            DecodedPacket packet = new DecodedPacket(PacketType.CHAR_SELECT_REQUEST, new CharSelectRequest(characterId));

            CharSelectResponse resp = gateway.request(packet, CharSelectResponse.class);
            if (resp.code() != ResponseCode.OK) {
                debugLog.log("CHAR_SELECT_ERR " + resp.code() + " " + resp.errorMessage());
                return;
            }

            CharacterSummary selectedChar = resp.selectedCharacter();
            clientState.onCharacterSelected(resp.code(), selectedChar, resp.errorMessage());

            CharacterSelectedState next = selectedProvider.get();
            next.init(accountId, selectedChar.characterId(), selectedChar.name(), selectedChar.zoneId());
            stateService.changeState(() -> next);
        } catch (Exception e) {
            debugLog.log("CHAR_SELECT_ERR " + e.getMessage());
        }
    }

    private void doDelete(long characterId) {
        try {
            DecodedPacket packet = new DecodedPacket(PacketType.CHAR_DELETE_REQUEST, new CharDeleteRequest(characterId));

            CharDeleteResponse resp = gateway.request(packet, CharDeleteResponse.class);
            if (resp.code() == ResponseCode.OK) {
                debugLog.log("CHAR_DELETE_OK id=" + characterId);
                clientState.onCharacterDeleted(resp.code(), characterId, null);
                refreshCharacters();
            } else {
                debugLog.log("CHAR_DELETE_ERR " + resp.code() + " " + resp.errorMessage());
            }
        } catch (Exception e) {
            debugLog.log("CHAR_DELETE_ERR " + e.getMessage());
        }
    }

    private void doCreate() {
        try {
            CharCreateRequest reqPayload = new CharCreateRequest(
              panel.getNewName(),
              panel.getRaceId(),
              panel.getSizeId(),
              panel.getFaceId(),
              panel.getJobId(),
              panel.getNationName()
            );

            DecodedPacket packet = new DecodedPacket(PacketType.CHAR_CREATE_REQUEST, reqPayload);
            CharCreateResponse resp = gateway.request(packet, CharCreateResponse.class);

            if (resp.code() == ResponseCode.OK) {
                debugLog.log("CHAR_CREATE_OK id=" + resp.characterId());
                panel.hideCreateForm();
                clientState.onCharacterCreated(resp.code(), resp.characterId(), null);
                refreshCharacters();
            } else {
                debugLog.log("CHAR_CREATE_ERR " + resp.code() + " " + resp.errorMessage());
            }
        } catch (Exception e) {
            debugLog.log("CHAR_CREATE_ERR " + e.getMessage());
        }
    }
}