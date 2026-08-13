package catalyst.common.dto.lobby;

/**
 * Client request to create a new character, matching the pure FFXI customization domain.
 * Contextual account identity is injected out-of-band by the Gateway via ClientSession.
 *
 * @param name Desired character display name.
 * @param race Selected race ID.
 * @param size Selected character body size ID.
 * @param face Selected facial model/hair texture ID.
 * @param mainJob Selected starting job class ID.
 * @param nation Selected starting nation (e.g. San d'Oria, Bastok, Windurst).
 */
public record CharCreateRequest(
  String name,
  int race,
  int size,
  int face,
  int mainJob,
  String nation
) {
  public CharCreateRequest {
    if (name != null) {
      name = name.trim();
    }
    if (nation != null) {
      nation = nation.trim();
    }
  }
}