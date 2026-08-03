# Spec: Local-Only Runtime Mode

## Purpose

Enable client-side development workflows that do not require a running server.

## Current State (Milestone 1)

Local-only mode is a lightweight bootstrap that:

- Bypasses all network/auth paths
- Sets a synthetic session string (`LOCAL-<timestamp>`)
- Displays a status message indicating the local zone and preset character name
- Keeps keepalive disabled (no PING loop)

Movement, camera, and actual zone rendering are **out of scope for Milestone 1**. The mode exists to verify the mode-switching and UI phase-locking path without server dependency.

## Functional Requirements

- `mode=LOCAL` radio button available at all times (including before any connection attempt).
- Switching to local mode while a remote session is active triggers `gracefulDisconnect`.
- Switching to remote mode from local resets auth state.
- The UI clearly indicates local mode in the status bar.
- No `accounts_sessions` row is created or modified by local mode.

## Configuration Inputs (Current)

- Zone: hardcoded to zone 230 (Southern San d'Oria) as a placeholder
- Character: preset name "LocalDev"
- No config file or launcher flag yet; selection is via the UI radio button

## Future Scope

When zone rendering is implemented, local-only mode will:

- Accept a configurable zone ID and spawn transform
- Load zone geometry and assets from local DAT files
- Support movement and camera controls
- Use the same render pipeline as server-connected mode (injection point exists)

## Milestone 1 Done Criteria

- [x] Client starts and remains fully functional without a running server
- [x] "Enter Local Zone" sets synthetic session state and updates status bar
- [x] Keepalive loop does not run in local mode
- [x] Switching from remote to local gracefully disconnects active sessions
- [x] Status bar clearly shows `LOCAL` mode
