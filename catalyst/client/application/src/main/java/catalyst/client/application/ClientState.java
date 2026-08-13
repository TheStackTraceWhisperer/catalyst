package catalyst.client.application;

import catalyst.client.network.listener.LobbyNetworkListener;
import catalyst.client.network.listener.SessionNetworkListener;
import catalyst.common.dto.lobby.CharacterSummary;
import catalyst.common.network.ResponseCode;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Getter
@Singleton
public class ClientState implements SessionNetworkListener, LobbyNetworkListener {

  public enum AppPhase {
    DISCONNECTED,
    AUTHENTICATING,
    LOBBY_CHAR_SELECT,
    LOBBY_CHAR_CREATE,
    ENTERING_WORLD,
    IN_WORLD
  }

  private volatile AppPhase phase = AppPhase.DISCONNECTED;
  private volatile Long accountId;
  private volatile Long selectedCharacterId;
  private volatile Integer currentZoneId;
  private volatile String lastErrorMessage;

  private final List<CharacterSummary> characterList = new CopyOnWriteArrayList<>();

  // --- SessionNetworkListener Implementation ---

  @Override
  public void onAuthenticated(long accountId) {
    this.accountId = accountId;
    this.lastErrorMessage = null;
    this.phase = AppPhase.LOBBY_CHAR_SELECT;
    log.info("ClientState -> AUTHENTICATED accountId={}", accountId);
  }

  @Override
  public void onAuthenticationFailed(ResponseCode code, String errorMessage) {
    this.lastErrorMessage = errorMessage != null ? errorMessage : code.name();
    this.phase = AppPhase.DISCONNECTED;
    log.warn("ClientState -> AUTH_FAILED code={}, msg={}", code, errorMessage);
  }

  @Override
  public void onLoggedOut() {
    reset();
    log.info("ClientState -> LOGGED_OUT");
  }

  // --- LobbyNetworkListener Implementation ---

  @Override
  public void onCharacterListReceived(ResponseCode code, List<CharacterSummary> characters, String errorMessage) {
    if (code == ResponseCode.OK) {
      this.characterList.clear();
      if (characters != null) {
        this.characterList.addAll(characters);
      }
      this.lastErrorMessage = null;
      log.info("ClientState -> Updated character list count={}", this.characterList.size());
    } else {
      this.lastErrorMessage = errorMessage;
      log.error("ClientState -> Failed to receive character list: {}", errorMessage);
    }
  }

  @Override
  public void onCharacterCreated(ResponseCode code, Long characterId, String errorMessage) {
    if (code == ResponseCode.OK) {
      this.lastErrorMessage = null;
      this.phase = AppPhase.LOBBY_CHAR_SELECT;
      log.info("ClientState -> Character created successfully ID={}", characterId);
    } else {
      this.lastErrorMessage = errorMessage;
      log.warn("ClientState -> Character creation failed: {}", errorMessage);
    }
  }

  @Override
  public void onCharacterDeleted(ResponseCode code, long characterId, String errorMessage) {
    if (code == ResponseCode.OK) {
      this.characterList.removeIf(c -> c.characterId() == characterId);
      this.lastErrorMessage = null;
      log.info("ClientState -> Removed character ID={}", characterId);
    } else {
      this.lastErrorMessage = errorMessage;
      log.warn("ClientState -> Character deletion failed: {}", errorMessage);
    }
  }

  @Override
  public void onCharacterSelected(ResponseCode code, CharacterSummary character, String errorMessage) {
    if (code == ResponseCode.OK && character != null) {
      this.selectedCharacterId = character.characterId();
      this.lastErrorMessage = null;
      log.info("ClientState -> Selected character ID={}", character.characterId());
    } else {
      this.lastErrorMessage = errorMessage;
      log.warn("ClientState -> Character selection failed: {}", errorMessage);
    }
  }

  @Override
  public void onWorldBound(ResponseCode code, long characterId, int targetZoneId, String errorMessage) {
    if (code == ResponseCode.OK) {
      this.selectedCharacterId = characterId;
      this.currentZoneId = targetZoneId;
      this.lastErrorMessage = null;
      this.phase = AppPhase.ENTERING_WORLD;
      log.info("ClientState -> ENTERING_WORLD (Zone={})", targetZoneId);
    } else {
      this.lastErrorMessage = errorMessage;
      log.error("ClientState -> World binding failed: {}", errorMessage);
    }
  }

  public void setPhase(AppPhase phase) {
    this.phase = phase;
  }

  public void reset() {
    this.phase = AppPhase.DISCONNECTED;
    this.accountId = null;
    this.selectedCharacterId = null;
    this.currentZoneId = null;
    this.lastErrorMessage = null;
    this.characterList.clear();
  }
}