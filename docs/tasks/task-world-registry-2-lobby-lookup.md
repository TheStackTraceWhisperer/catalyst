# Task: World Registry Integration — Lobby Registry Lookup on PLAY

**Priority:** High (blocks horizontal world server scaling)  
**Area:** Lobby Service  
**Effort:** Small (1-2 days)  
**Parent Task:** task-world-registry-integration.md  
**Depends On:** task-world-registry-1-zone-registration.md

## Purpose

Replace the hardcoded zone-to-DNS string format (`"world-service-" + zoneId + ":35556"`) in `PlayRequestHandler` with a live lookup against the Redis zone registry, so the lobby dynamically routes players to whichever world server instance is actually hosting their zone.

## What Needs to Happen

- Inject `ZoneRegistry` into `PlayRequestHandler` (lobby-service).
- On a `PlayRequest`:
  - Resolve the character's `currentZoneId`.
  - Call `registry.lookup(zoneId)` to get the live `host:port`.
  - If the registry returns empty (no world server registered for that zone), return an error `PlayResponse` with an appropriate message rather than routing to a dead address.
  - Use the resolved `host:port` as the `worldAddress` in the `play_success` control message.
- Remove the hardcoded `"world-service-" + id.currentZoneId() + ":35556"` string.

## Acceptance Criteria

- `PlayRequest` for a character in zone 231 resolves the actual world server address from Redis.
- If no world server is registered for the requested zone, the client receives a meaningful error.
- The existing hardcoded DNS format string is fully removed from `PlayRequestHandler`.
- E2E test harness still passes end-to-end.
