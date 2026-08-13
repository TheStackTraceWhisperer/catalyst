package catalyst.common.dto.lobby;

/**
 * Client request to permanently delete a character.
 *
 * @param characterId Target character ID to delete.
 */
public record CharDeleteRequest(long characterId) {}