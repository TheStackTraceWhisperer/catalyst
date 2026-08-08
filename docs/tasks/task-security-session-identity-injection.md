# Task: Gateway Session Identity Injection

**Priority:** Sooner  
**Area:** Security / Gateway

## Problem

Currently, world-bound requests include a `sessionId` field inside the frame payload that is
**provided by the client**. The world service handlers read this `sessionId` from the inbound DTO
to identify which player session a packet belongs to.

This is a layering and security violation. The client should never be trusted to self-report its
own identity. A modified client could forge any `sessionId` and manipulate another player's session.

We already solved an analogous problem with connection state: the gateway tracks `AUTHENTICATED`
and `PLAYING` state on the channel attribute — the client is never asked which state it is in. The
same pattern needs to apply to `sessionId`.

## What Needs to Happen

### Gateway Side
- After `play_success` is received from the lobby service, the gateway currently stores the
  `WorldClient` on the channel attribute. It should **also** store the verified `sessionId` as a
  channel attribute at this point, since `sessionId` is present in the control message payload.
- All outbound `FLAG_WORLD` `GatewayFrame` requests should have the `sessionId` header injected by
  the gateway before forwarding to the backend — not sourced from the client frame body.

```java
// New AttributeKey
public static final AttributeKey<String> SESSION_ID_KEY = AttributeKey.valueOf("gateway.sessionId");

// On play_success:
parentChannel.attr(SESSION_ID_KEY).set(gcm.sessionId());
```

### Backend Side (World Service)
- World service packet handlers should receive the `sessionId` from the channel attribute context
  rather than the DTO.
- The DTO fields for `sessionId` on world-bound requests (e.g., `PingRequest`, `LogoutRequest`)
  can be removed entirely from the client-facing DTOs once the gateway injects them server-side.

## Design Note
This is the same principle as mTLS client certificate validation at the transport layer — identity
is established at connection/session setup time and bound to the channel, never re-supplied by the
peer per-request.

## Acceptance Criteria
- World service handlers do not read `sessionId` from the inbound DTO.
- The gateway injects the verified `sessionId` onto all forwarded world frames.
- Client-side DTOs for world requests no longer include a `sessionId` field.
- E2E test harness still passes (the gateway handles the injection transparently).
