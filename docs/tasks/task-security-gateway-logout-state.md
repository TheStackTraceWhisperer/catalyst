# Task: Gateway Logout State Transition

**Priority:** Sooner  
**Area:** Security / Gateway

## Problem

In `gateway/transport/RequestHandler.java`, the gateway tracks per-connection state via a
`ConnectionState` channel attribute (`UNAUTHENTICATED` → `AUTHENTICATED` → `PLAYING`).

When the `play_success` control message arrives, the state advances to `PLAYING`. However, when
the client sends a `LogoutRequest` and the world service returns a successful `LogoutResponse`,
the gateway **never transitions the state back** to `AUTHENTICATED`.

This means after logout, the player's connection still has `PLAYING` state, and the gateway
continues routing all `FLAG_WORLD` frames to the assigned world server — even though the session
has been destroyed. A player could re-enter game state or replay world-bound frames without
re-selecting a character.

## What Needs to Happen

- The world service should emit a `logout_success` control message in the `GatewayControlMessage`
  alongside the `LogoutResponse`, similar to how `play_success` is signaled.
- `RequestHandler.handleStateTransitions(...)` must handle `"logout_success"`:
  - Transition the channel attribute from `PLAYING` back to `AUTHENTICATED`.
  - Clear the `WORLD_CLIENT_KEY` attribute from the parent channel so the player's world server
    affinity is removed.

```java
case "logout_success" -> {
    log.info("Client logged out, transitioning to AUTHENTICATED");
    parentChannel.attr(STATE_KEY).set(ConnectionState.AUTHENTICATED);
    parentChannel.attr(WORLD_CLIENT_KEY).set(null);
}
```

## Acceptance Criteria
- After a successful logout, the connection state is `AUTHENTICATED`.
- `FLAG_WORLD` frames sent after logout are rejected by the gateway (no world state).
- A player can re-select a character and enter the world again in the same connection without reconnecting.
- E2E test harness verifies logout → re-select → re-play flow.
