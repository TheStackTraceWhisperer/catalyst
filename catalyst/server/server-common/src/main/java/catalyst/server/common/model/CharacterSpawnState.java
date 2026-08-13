package catalyst.server.common.model;

/**
 * Server-internal snapshot of a character's spatial spawn location
 * and world coordinates used during zone transfers and session handoffs.
 */
public record CharacterSpawnState(
  long characterId,
  String name,
  int homeZoneId,
  float homeX,
  float homeY,
  float homeZ,
  float homeRot,
  int currentZoneId,
  float currentX,
  float currentY,
  float currentZ,
  float currentHeading
) {
  public float currentRot() {
    return currentHeading;
  }
}