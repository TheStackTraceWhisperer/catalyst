# Task: Gateway Logout State Transition [COMPLETED]

**Area:** Security / Gateway

## Status
**Completed:** The gateway now intercepts successful `logout_success` control signals to reset the connection security state and clear session affinity.

## Resolution Summary

- **Logout Transition**: When a client logs out, the world service processes the request and responds with a `logout_success` control message in the `GatewayControlMessage` payload.
- **State Cleanup**: Upon receiving `logout_success`, `RequestHandler` resets the channel's `SecurityState` back to `AUTHENTICATED` (formerly `ConnectionState` transition).
- **Affinity Removal**: The parent channel's `WORLD_CLIENT_KEY` and `SESSION_ID_KEY` attributes are explicitly set to `null`, tearing down the association with the world server.
- **Immediate Rejection**: Once transitioned back to `AUTHENTICATED`, the gateway's routing policy rejects any subsequent `FLAG_WORLD` packets sent by the client unless they go through the lobby character-select/play flow again.
