package catalyst.common.dto.lobby;

/**
 * Lightweight DTO representing a character preview on the selection screen.
 *
 * @param characterId Unique character ID.
 * @param name Character display name.
 * @param raceId Character race/model identifier.
 * @param mainJobId Main job class ID (e.g., WAR, RDM).
 * @param mainJobLevel Main job level.
 * @param zoneId Current zone location ID.
 */
public record CharacterSummary(
  long characterId,
  String name,
  int raceId,
  int mainJobId,
  int mainJobLevel,
  int zoneId
) {}