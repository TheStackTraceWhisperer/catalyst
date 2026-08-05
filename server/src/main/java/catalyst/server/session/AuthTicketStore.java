package catalyst.server.session;

import catalyst.server.config.ServerProperties;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class AuthTicketStore {

    private final ServerProperties props;

    private record Ticket(long accountId, long expiresAtMs) {}

    private final ConcurrentHashMap<String, Ticket> tickets = new ConcurrentHashMap<>();

    /** Issue a new auth token for the given account; returns the token string. */
    public String issue(long accountId) {
        String token = UUID.randomUUID().toString();
        long expiresAt = System.currentTimeMillis()
            + Duration.ofSeconds(props.getAuthTicketTimeoutSeconds()).toMillis();
        tickets.put(token, new Ticket(accountId, expiresAt));
        return token;
    }

    /**
     * Validate a token and return its accountId, or null if invalid/expired.
     * Extends expiry on each successful validation (rolling window).
     */
    public Long validate(String token) {
        if (token == null || token.isBlank()) return null;
        Ticket t = tickets.get(token);
        if (t == null) return null;
        long now = System.currentTimeMillis();
        if (t.expiresAtMs() <= now) {
            tickets.remove(token, t);
            return null;
        }
        long newExpiry = now + Duration.ofSeconds(props.getAuthTicketTimeoutSeconds()).toMillis();
        tickets.put(token, new Ticket(t.accountId(), newExpiry));
        return t.accountId();
    }

    /** Explicitly invalidate a token. */
    public void expire(String token) {
        if (token != null) tickets.remove(token);
    }

    /** Scheduled cleanup — call every 10s. */
    public int removeExpired() {
        long now = System.currentTimeMillis();
        int removed = 0;
        for (Map.Entry<String, Ticket> e : tickets.entrySet()) {
            if (e.getValue().expiresAtMs() <= now && tickets.remove(e.getKey(), e.getValue())) {
                removed++;
            }
        }
        if (removed > 0) log.info("AUTH_TICKET_CLEANUP removed={}", removed);
        return removed;
    }
}
