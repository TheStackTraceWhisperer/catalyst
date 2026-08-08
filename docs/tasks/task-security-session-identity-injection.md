# Task: Gateway Session Identity Injection [COMPLETED]

**Area:** Security / Gateway

## Status
**Completed:** The gateway now tracks and injects verified `sessionId` attributes transparently at the transport layer, and DTOs have been cleaned of client-supplied session fields.

## Resolution Summary

- **Session Injection**: When the gateway receives a successful `play_success` control message, it extracts the verified `sessionId` and binds it as a Netty channel attribute (`SESSION_ID_KEY`) on the parent connection.
- **DTO Decoupling**: For every inbound frame mapped to a `SESSION_BOUND` policy (like world packets), the gateway intercepts the request and injects the stored `sessionId` into the `GatewayFrame` envelope before forwarding it to the backend. The client no longer passes or manages its own session ID inside the DTO payloads (e.g. `PingRequest`, `LogoutRequest` are session-less).
- **Backend Safety**: The world service reads the `sessionId` from the transport frame context rather than relying on self-reported client fields, eliminating the risk of session forgery.
