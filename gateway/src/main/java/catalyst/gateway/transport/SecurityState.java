package catalyst.gateway.transport;

public enum SecurityState {
    UNAUTHENTICATED(1),
    AUTHENTICATED(2),
    SESSION_BOUND(3);

    private final int level;

    SecurityState(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }
}
