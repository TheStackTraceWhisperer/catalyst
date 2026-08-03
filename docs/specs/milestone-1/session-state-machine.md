# Spec: Session/Connection State Machine

## Purpose

Define authoritative session lifecycle and transition rules for client connections.

## States

- `CONNECTED`
- `AUTHENTICATING`
- `AUTHENTICATED`
- `IN_SESSION`
- `DISCONNECTING`
- `CLOSED`

## Allowed Transitions

- `CONNECTED -> AUTHENTICATING`
- `AUTHENTICATING -> AUTHENTICATED` (auth success)
- `AUTHENTICATED -> IN_SESSION` (identity + zone attach complete)
- `* -> DISCONNECTING` (client/server initiated close)
- `DISCONNECTING -> CLOSED`
- `* -> CLOSED` (fatal error/timeout)

## Rules

- Reject non-auth requests before `AUTHENTICATED`.
- Keep a unique `sessionId` and `connectionId`.
- Bind exactly one authenticated account per active session.
- Enforce exactly one active session per `accountId` and per `characterId`.
- Reconnect behavior must create a new connection context and close stale state.
- Session state transitions must remain synchronized with the backing `accounts_sessions` record.

## Milestone 1 Done Criteria

- State transitions are explicit, validated, and logged.
- Invalid transitions are rejected with protocol status code.
- Concurrent login attempts cannot produce two active sessions for the same account/character.
