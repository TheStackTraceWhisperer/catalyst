package catalyst.common.dto.lobby;

/**
 * Client request to enter the world with the currently selected character.
 *
 * @param characterId Target character ID entering the world.
 */
public record PlayRequest(long characterId) {}