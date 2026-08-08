# Task: Client Connection Resiliency

**Priority:** Medium  
**Area:** Client / Networking
**Effort:** Small (1-2 days)  

## Problem

The current `QuicGatewayService` establishes a single QUIC connection via `connect(host, port)` on
login and reuses it for the entire session. There is no handling for:

- The underlying UDP connection dropping mid-session (e.g., network switch, sleep/wake).
- QUIC idle timeout expiry (currently configured to 60 seconds server-side).
- The client state machine being left in an indeterminate state after a connection loss.

## What Needs to Happen

### Detection
- Hook into Netty's `channelInactive` or `quicChannel.closeFuture()` to detect when the QUIC
  connection is closed unexpectedly.
- Feed a `ConnectionLostEvent` into the `ClientDispatcher` inbox so the render thread can handle
  it safely.

### Recovery in the State Machine
- `InGameState` and `CharacterSelectedState` must handle a `ConnectionLostEvent`:
  - Stop the `KeepAliveService`.
  - Transition the state machine back to `UnauthenticatedState` with an appropriate error message
    displayed in the login panel (e.g., "Connection lost. Please log in again.").

### Reconnect / Re-auth
- `QuicGatewayService` should clear its stored `host`, `port`, and internal channel state on
  disconnect so a fresh `connect(...)` call re-establishes cleanly.
- Optionally: implement automatic re-authentication using a stored auth token if the server
  supports token refresh.

## Acceptance Criteria
- Simulating a network drop (e.g., killing the gateway pod) causes the client to return to the
  login screen gracefully instead of hanging or crashing.
- Re-logging in after a connection drop works without restarting the client process.
