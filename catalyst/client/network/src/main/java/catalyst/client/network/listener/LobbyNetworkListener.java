package catalyst.client.network.listener;

import catalyst.common.dto.lobby.CharacterSummary;
import catalyst.common.network.ResponseCode;
import java.util.List;

public interface LobbyNetworkListener {
  void onCharacterListReceived(ResponseCode code, List<CharacterSummary> characters, String errorMessage);
  void onCharacterCreated(ResponseCode code, Long characterId, String errorMessage);
  void onCharacterDeleted(ResponseCode code, long characterId, String errorMessage);
  void onCharacterSelected(ResponseCode code, CharacterSummary character, String errorMessage);
  void onWorldBound(ResponseCode code, long characterId, int targetZoneId, String errorMessage);
}