package catalyst.server.common.network;

/**
 * Thread-local context to store the verified sessionId for the current execution thread.
 * This prevents handlers from needing to rely on client-supplied sessionIds in DTOs.
 */
public final class SessionContext {
    private static final ThreadLocal<String> CURRENT_SESSION_ID = new ThreadLocal<>();

    public static String getSessionId() {
        return CURRENT_SESSION_ID.get();
    }

    public static void setSessionId(String sessionId) {
        CURRENT_SESSION_ID.set(sessionId);
    }

    public static void clear() {
        CURRENT_SESSION_ID.remove();
    }

    private SessionContext() {}
}
