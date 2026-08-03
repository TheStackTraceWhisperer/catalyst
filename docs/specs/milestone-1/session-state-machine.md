# Spec: Session/Connection State Machine

## Purpose

Define the authoritative session lifecycle and transition rules.

## States

| State | Description |
|---|---|
| `UNAUTHENTICATED` | Connection established, no valid auth token |
| `AUTHENTICATED` | Auth token issued, character not yet selected |
| `CHARACTER_SELECTED` | Character identity loaded, no game session yet |
| `IN_GAME` | `accounts_sessions` row exists, keepalive active |
| `DISCONNECTING` | Logout initiated or timeout triggered |

## Allowed Transitions

```
UNAUTHENTICATED
    → AUTHENTICATED          (LOGIN with valid credentials)

AUTHENTICATED
    → CHARACTER_SELECTED     (CHAR_SELECT with valid authToken + characterId)
    → UNAUTHENTICATED        (Sign out / authToken expiry)

CHARACTER_SELECTED
    → IN_GAME                (PLAY — creates accounts_sessions row)
    → AUTHENTICATED          (de-select, back to character list)
    → UNAUTHENTICATED        (Sign out)

IN_GAME
    → DISCONNECTING          (LOGOUT, window close, or server timeout)

DISCONNECTING
    → UNAUTHENTICATED        (cleanup complete)
```

Any state can transition to `UNAUTHENTICATED` on fatal error or explicit sign out.

## Key Rules

- Only `LOGIN` with valid Argon2id-verified credentials may enter `AUTHENTICATED`.
- Only a valid `authToken` (in-memory, not expired) allows `CHAR_SELECT` and `PLAY`.
- `CHAR_SELECT` validates and loads character identity but **does not create a session record**.
- `PLAY` creates the `accounts_sessions` row. If a row already exists for this account or character, returns `ALREADY_ONLINE`.
- `PING` is only valid in `IN_GAME`; server returns `SESSION_NOT_FOUND` if no matching row exists.
- `LOGOUT` removes the `accounts_sessions` row and transitions to `UNAUTHENTICATED`.
- Server-side timeout (30s without PING) removes the row and transitions the server state; the client discovers this on the next failed PING.

## Double-Login Prevention

Enforced by database uniqueness constraints on `accounts_sessions`:

- `UNIQUE (account_id)` — one active session per account
- `UNIQUE (character_id)` — one active session per character

A `23505` (unique violation) on `PLAY` returns `ALREADY_ONLINE`.

## Auth Token vs Session ID

| Property | Auth Token | Session ID |
|---|---|---|
| Created by | `LOGIN` | `PLAY` |
| Stored in | Server in-memory map | `accounts_sessions` table (UUID) |
| Expiry | 300s rolling | 30s without PING |
| Purpose | Pre-session request gating | Active game session identity |
| Client uses it for | CHAR_*, PLAY | PING, LOGOUT |

## Logging

All state transitions are logged at INFO level with account ID, character ID (where applicable), and the triggering message type.

## Milestone 1 Done Criteria

- [x] State transitions follow the rules above
- [x] `CHAR_SELECT` does not create a session; `PLAY` does
- [x] Double-login returns `ALREADY_ONLINE` on both account and character dimensions
- [x] Timeout cleanup matches explicit logout cleanup behavior
- [x] Client UI reflects each phase via phase-locked control visibility
