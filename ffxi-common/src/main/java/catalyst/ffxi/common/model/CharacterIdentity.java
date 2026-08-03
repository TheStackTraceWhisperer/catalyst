package catalyst.ffxi.common.model;

public record CharacterIdentity(
    String characterId,
    String name,
    int homeZoneId,
    float homeX,
    float homeY,
    float homeZ,
    float homeHeading,
    int currentZoneId,
    float currentX,
    float currentY,
    float currentZ,
    float currentHeading
) {
}
