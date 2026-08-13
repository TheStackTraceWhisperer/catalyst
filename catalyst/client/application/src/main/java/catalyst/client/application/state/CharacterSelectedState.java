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
public class CharacterSelectedState implements ApplicationState {

    private final CharacterPanel panel;
    private final DebugLogPanel debugLog;
    private final ClientTransportService gateway;
    private final ApplicationStateService stateService;
    private final ClientState clientState;
    private final BeanProvider<UnauthenticatedState> unauthProvider;
    private final BeanProvider<InGameState> inGameProvider;

    private long accountId;
    private long characterId;
    private String characterName;
    private int currentZoneId;

    public void init(long accountId, long characterId, String characterName, int currentZoneId) {
        this.accountId = accountId;
        this.characterId = characterId;
        this.characterName = characterName;
        this.currentZoneId = currentZoneId;
    }

    @Override
    public void onEnter() {
        refreshCharacters();
        debugLog.log("CHAR_SELECT_OK " + characterName + " zone=" + currentZoneId);
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
            clientState.reset();
            stateService.changeState(unauthProvider::get);
        }
        if (panel.getSelectCharacterId() != null) doSelect(panel.getSelectCharacterId());
        if (panel.getDeleteCharacterId() != null) doDelete(panel.getDeleteCharacterId());
        if (panel.isCreateSubmitted()) doCreate();
        if (panel.isPlayRequested()) doPlay();
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

    private void doSelect(long targetCharId) {
        try {
            DecodedPacket packet = new DecodedPacket(PacketType.CHAR_SELECT_REQUEST, new CharSelectRequest(targetCharId));

            CharSelectResponse resp = gateway.request(packet, CharSelectResponse.class);
            if (resp.code() != ResponseCode.OK) {
                debugLog.log("CHAR_SELECT_ERR " + resp.code() + " " + resp.errorMessage());
                return;
            }

            CharacterSummary selectedChar = resp.selectedCharacter();
            this.characterId = selectedChar.characterId();
            this.characterName = selectedChar.name();
            this.currentZoneId = selectedChar.zoneId();

            clientState.onCharacterSelected(resp.code(), selectedChar, resp.errorMessage());
            debugLog.log("CHAR_SELECT_OK " + characterName + " zone=" + currentZoneId);
        } catch (Exception e) {
            debugLog.log("CHAR_SELECT_ERR " + e.getMessage());
        }
    }

    private void doDelete(long targetCharId) {
        try {
            DecodedPacket packet = new DecodedPacket(PacketType.CHAR_DELETE_REQUEST, new CharDeleteRequest(targetCharId));

            CharDeleteResponse resp = gateway.request(packet, CharDeleteResponse.class);
            if (resp.code() == ResponseCode.OK) {
                debugLog.log("CHAR_DELETE_OK id=" + targetCharId);
                clientState.onCharacterDeleted(resp.code(), targetCharId, null);
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

    private void doPlay() {
        try {
            DecodedPacket packet = new DecodedPacket(PacketType.PLAY_REQUEST, new PlayRequest(characterId));
            PlayResponse resp = gateway.request(packet, PlayResponse.class);

            if (resp.code() != ResponseCode.OK) {
                debugLog.log("PLAY_ERR " + resp.code() + " " + resp.errorMessage());
                return;
            }

            int zoneId = resp.targetZoneId();
            debugLog.log("PLAY_OK zone=" + zoneId);

            clientState.onWorldBound(resp.code(), characterId, zoneId, null);

            InGameState next = inGameProvider.get();
            next.init(accountId, characterId, characterName, zoneId, 5000L);
            stateService.changeState(() -> next);
        } catch (Exception e) {
            debugLog.log("PLAY_ERR " + e.getMessage());
        }
    }
}