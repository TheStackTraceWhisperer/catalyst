# Spec: Heartbeat and Timeout Handling

## Purpose

Keep game sessions healthy and reclaim stale connections deterministically.

## Timing (Implemented)

| Parameter | Value | Notes |
|---|---|---|
| Client ping interval | 5 seconds | `KEEPALIVE_INTERVAL_MS = 5000` in `ClientMain` |
| Server timeout threshold | 30 seconds | `SESSION_TIMEOUT_SECONDS = 30` in `ServerMain` |
| Server cleanup interval | 10 seconds | Scheduled task in `ServerMain` |

## Protocol

| Message | Direction | Fields |
|---|---|---|
| `PING` | Client → Server | `sessionId` (UUID string) |
| `PONG` | Server → Client | `sessionId` |
| `SESSION_NOT_FOUND` error | Server → Client | When sessionId does not match any active row |

`PING` is only valid after `PLAY` has created a game session.

## Server-Side Rules

- Any valid `PING` updates `last_seen_at = NOW()` on the matching `accounts_sessions` row.
- The cleanup scheduler runs every 10 seconds and:
  1. Collects all sessions where `last_seen_at < NOW() - 30s`
  2. Calls `leaveZone(sessionId, zoneId)` for each (updates in-memory zone population)
  3. Deletes the stale rows from `accounts_sessions`
  4. Logs `SESSION_CLEANUP removed=N`

## Client-Side Rules

- Client tracks `lastPingAtMs` per session.
- On each ImGui render frame, `maybeKeepAlive()` is called — if `now - lastPingAtMs >= 5000ms` and a session is active, a PING is sent.
- A "Ping now" button is available in the in-game UI for manual trigger.
- Client displays: keepalive status (`ok`/`failed`), last RTT in ms, and last successful PING timestamp.
- If PING fails (connection error or `SESSION_NOT_FOUND`), keepalive status shows the error reason.

## Zone Cleanup on Timeout

When a session is cleaned up (by timeout or logout), the server calls `leaveZone(sessionId, zoneId)`, which decrements the in-memory zone population count and logs `ZONE_LEAVE`.

## Milestone 1 Done Criteria

- [x] Client sends PING every 5 seconds once a game session is active
- [x] Server updates `last_seen_at` on each valid PING
- [x] Server scheduler removes stale sessions after 30s of inactivity
- [x] Zone population is decremented correctly on timeout cleanup
- [x] Client displays RTT and last successful PING timestamp in status bar
