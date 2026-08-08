# Task: World Session Expiry & Cleanup

**Priority:** Medium  
**Area:** World Service / Session Management
**Effort:** Small (1-2 days)  

## Problem

When a client disconnects abnormally (process kill, crash, network drop) without sending a
`LogoutRequest`, the player's session record remains in the database indefinitely. The player's
entity stays registered in the zone. No cleanup occurs.

The `KeepAliveService` on the client sends a `PingRequest` every N seconds (configured via
`keepaliveIntervalMs` in `PlayResponse`). The world service already processes `PingRequest` and
updates a last-seen timestamp. This heartbeat mechanism is the foundation for expiry detection.

## What Needs to Happen

### Session Timestamp Tracking
- `WorldPingRequestHandler` must update a `lastSeenAt` timestamp on the session record in the
  database every time a valid ping is received.
- The `SessionRepository` should expose an `updateLastSeen(sessionId, Instant)` method.

### Expiry Sweep (Tick Listener)
- Implement a `SessionExpirySystem` that hooks into the `ZoneMessageDispatcher` tick loop as
  a registered `TickListener` (see the `TickListener` interface design discussed).
- Every N ticks (e.g., every 300 ticks = 30 seconds at 10Hz), it queries all sessions whose
  `lastSeenAt` is older than 30 seconds.
- For each expired session:
  1. Remove the player entity from the zone entity registry.
  2. Broadcast a despawn notification to other zone members.
  3. Delete or invalidate the session record in the database.
  4. Log the expiry event.

### Configuration
- The expiry threshold (e.g., 30 seconds) should be configurable via `ServerProperties` or
  application configuration, not hardcoded.

## Acceptance Criteria
- A client that stops sending pings has its session cleaned up within ~30 seconds.
- Other players in the same zone receive a despawn notification when the session expires.
- The cleaned-up session ID is no longer valid for subsequent requests (the gateway or world
  service rejects it).
- Normal logout is unaffected.
