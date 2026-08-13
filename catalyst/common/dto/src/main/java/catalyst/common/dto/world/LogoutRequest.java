package catalyst.common.dto.world;

/**
 * Client request to gracefully terminate a session or disconnect from the world.
 * Context identity is supplied out-of-band via the Gateway's ClientSession.
 */
public record LogoutRequest() {}