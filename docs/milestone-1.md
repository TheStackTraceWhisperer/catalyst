# Milestone 1: Connection, Authentication, and Session Kernel

## Goal

Deliver a thin end-to-end slice that proves the client/server backbone:

1. Client establishes a QUIC connection to the server.
2. Client authenticates and receives an auth token.
3. Client lists, creates, selects, and deletes characters.
4. Client enters a game session by selecting a character and clicking Play.
5. Server creates and manages a session lifecycle with heartbeat and timeout.
6. Disconnect/reconnect and cleanup paths work reliably.

## Included Systems

- Account/auth model (Argon2id, auth token issuance)
- Account/session record structures
- Character lifecycle: create, list, select, delete (soft delete)
- Character creation with LSB-aligned fields (race 1-8, body size, face 0-15, starting job, nation)
- Session/connection state machine (UNAUTHENTICATED → AUTHENTICATED → CHARACTER_SELECTED → IN_GAME)
- QUIC transport (Netty incubator, TLS 1.3, self-signed cert for dev)
- LWJGL + Dear ImGui client shell with phase-locked UI
- Debug log viewer
- Zone routing skeleton (in-memory zone population tracking)
- Heartbeat/timeout (5s client interval, 30s server timeout)
- Local-only zone bootstrap path (dev mode)
- Basic character identity payload

## Out of Scope

- Combat, AI, inventory, full zone simulation
- Script engine behavior
- Gateway / multi-server split (single `ServerMain` in M1)
- Movement and camera (local-only mode bootstraps a zone but no movement yet)

## Transport

All client/server communication uses **QUIC over UDP** (Netty incubator codec, protocol `ffxi-1`). Each request/response pair uses a dedicated bidirectional QUIC stream on a persistent `QuicChannel`. The wire format is pipe-delimited `MessageFrame` (`TYPE|key=value|key=value\n`).

## Milestone Acceptance Criteria

- [x] Client connects to server over QUIC / TLS 1.3
- [x] Client provides an ImGui login window with username/password entry
- [x] Login returns an auth token; session is not created until PLAY
- [x] Client displays character list after login
- [x] Client can create a character (race 1-8 LSB encoding, body size, face 0-15, starting job 1-6, nation 0-2)
- [x] Client can select a character and click Play to enter a game session
- [x] Client can soft-delete a character (deleted_at, excluded from active list)
- [x] Server validates character creation fields (race rules, size constraints for Tarutaru/Galka, face range, starting job clamp, nation range)
- [x] Nation selects a random starting zone from the nation's zone pool on character creation
- [x] Server enforces double-login prevention (unique account + character constraints in accounts_sessions)
- [x] Heartbeat (PING/PONG) is active once a game session is established
- [x] Session timeout cleanup runs on a server scheduler (30s threshold)
- [x] Graceful disconnect sends LOGOUT on window close or mode switch
- [x] Client provides in-client debug log viewer with auto-scroll and clear
- [x] Client can start in local-only mode without a running server
- [x] Host/port fields are locked in the UI once authenticated
- [x] Character management UI is hidden while a game session is active
- [x] Character create form is hidden until explicitly opened

## Phase Flow

```
UNAUTHENTICATED
    │ LOGIN
    ▼
AUTHENTICATED (authToken valid 300s, rolling)
    │ CHAR_SELECT
    ▼
CHARACTER_SELECTED (identity loaded, no session yet)
    │ PLAY
    ▼
IN_GAME (sessionId created, keepalive active, zone joined)
    │ LOGOUT / timeout / window close
    ▼
UNAUTHENTICATED
```

## Specifications

- [Account/Auth Model](./specs/milestone-1/account-auth-model.md)
- [Account/Session Record Structures](./specs/milestone-1/account-session-record-structures.md)
- [Character Identity Payload](./specs/milestone-1/character-identity-payload.md)
- [Session State Machine](./specs/milestone-1/session-state-machine.md)
- [Client Shell UI](./specs/milestone-1/client-shell-ui.md)
- [Local-Only Runtime Mode](./specs/milestone-1/local-only-runtime-mode.md)
- [Protocol Status Conventions](./specs/milestone-1/protocol-status-conventions.md)
- [Heartbeat and Timeout](./specs/milestone-1/heartbeat-timeout.md)
- [Zone Routing Skeleton](./specs/milestone-1/zone-routing-skeleton.md)
