package catalyst.common.dto.lobby;

/**
 * Client request to fetch the list of characters belonging to the authenticated account.
 * Identity context is supplied via Gateway's ClientSession.
 */
public record CharListRequest() {}