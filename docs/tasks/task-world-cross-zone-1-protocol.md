# Task: Cross-Zone Handover — Zone Transition Protocol & DTOs

**Priority:** Low (depends on World Registry and Entity Spawning)  
**Area:** World Service / Common  
**Effort:** Small (1-2 days)  
**Parent Task:** task-world-cross-zone-handover.md

## Purpose

Define the data contracts and the initial protocol flow for handing a player's session from one world server to another at a zone boundary. Everything else in the handover depends on these types existing first.

## What Needs to Happen

- Add `ZoneTransitionRequest { int characterId, int targetZoneId, float x, float y, float z, float rot }` to `common-dto` under `catalyst.common.dto.world`.
- Add `ZoneTransitionResponse { int characterId, int zoneId, String sessionId, ResponseCode code, String message }` to the same package.
- Add `SessionHandoverRequest { String sessionId, int characterId, int targetZoneId, float x, float y, float z, float rot /*, future: stats snapshot */ }` as an internal world-to-world DTO (can live in a server-only module or `common-dto` with a note it is server-internal).
- Add `zone_transition` as a new control command to `GatewayControlMessage`, with a `newWorldAddress` field carrying the target world server's `host:port`.
- Register all new DTOs in the relevant service Fory registration lists (see `task-security-fory-class-registration.md`).

## Acceptance Criteria

- All new DTO records compile cleanly across all dependent modules.
- `zone_transition` is a recognised command in `RequestHandler.handleStateTransitions(...)` (update the channel's `WORLD_CLIENT_KEY` to the new address when received).
- E2E test harness still passes with the new type registrations in place.
