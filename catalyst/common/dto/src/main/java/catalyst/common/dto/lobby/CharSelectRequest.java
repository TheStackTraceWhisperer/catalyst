package catalyst.common.dto.lobby;

/**
 * Client request to select a character on the lobby menu.
 *
 * @param characterId Selected character ID.
 */
public record CharSelectRequest(long characterId) {}