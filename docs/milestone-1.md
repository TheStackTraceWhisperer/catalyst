# Milestone 1: Connection, Authentication, and Session Kernel

## Goal

Deliver a thin end-to-end slice that proves the client/server backbone:

1. Client establishes a QUIC connection.
2. Client authenticates with the server.
3. Server creates and manages a session lifecycle.
4. Client receives authenticated session confirmation.
5. Disconnect/reconnect and cleanup paths work reliably.

## Included Systems (LSB-inspired subset)

- Account/auth model
- Account/session record structures (LSB-equivalent)
- Session/connection state machine
- LWJGL + Dear ImGui client shell
- Basic character identity payload
- Zone routing skeleton
- Protocol status/error conventions
- Heartbeat/timeout handling
- Local-only zone bootstrap path (dev mode)

## Out of Scope

- Combat
- AI
- Inventory systems
- Script engine behavior
- Full zone simulation

## Milestone Acceptance Criteria

- Client can connect and complete login over QUIC.
- Client provides an ImGui login window for credential entry and login submission.
- Client provides an in-client debug log viewer for connection/auth/session diagnostics.
- Client can start in local-only mode and load a preset character directly into a configured zone.
- Server transitions session state correctly and enforces auth gating.
- Server returns a minimal character identity payload after auth.
- Session attaches to a zone context placeholder.
- Heartbeat and timeout rules are enforced.
- Double-login prevention is enforced at account and character levels.
- Session cleanup is deterministic on disconnect/timeouts.

## Specifications

- [Account/Auth Model](./specs/milestone-1/account-auth-model.md)
- [Account/Session Record Structures](./specs/milestone-1/account-session-record-structures.md)
- [Session State Machine](./specs/milestone-1/session-state-machine.md)
- [Client Shell UI](./specs/milestone-1/client-shell-ui.md)
- [Local-Only Runtime Mode](./specs/milestone-1/local-only-runtime-mode.md)
- [Character Identity Payload](./specs/milestone-1/character-identity-payload.md)
- [Zone Routing Skeleton](./specs/milestone-1/zone-routing-skeleton.md)
- [Protocol Status Conventions](./specs/milestone-1/protocol-status-conventions.md)
- [Heartbeat and Timeout](./specs/milestone-1/heartbeat-timeout.md)
