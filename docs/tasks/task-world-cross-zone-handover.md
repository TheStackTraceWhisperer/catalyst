# Task: Cross-Zone Handover

**Priority:** Low (depends on World Registry and Entity Spawning)  
**Area:** World Service / Gateway / Lobby Service
**Effort:** Medium (3-5 days)  

## Problem

When a player moves from one zone to another (e.g., zone transition trigger, warp, or airship),
the following must happen atomically from the player's perspective:

1. Their session must be handed from World Server A (hosting zone X) to World Server B (hosting zone Y).
2. Their entity must be despawned from zone X and spawned into zone Y.
3. The gateway must update its routing table to point the player's connection to World Server B.
4. The client must receive the new zone data and transition its in-game state seamlessly.

This is a distributed coordination problem involving the gateway, lobby service, and two world
server instances.

## What Needs to Happen

### Zone Transition Request
- Define a `ZoneTransitionRequest { characterId, targetZoneId, x, y, z, rot }` DTO.
- World Server A initiates the handover when a player crosses a zone boundary.

### Handover Protocol
1. **World Server A** contacts the **World Registry** (Redis) to find World Server B's address.
2. **World Server A** sends a `SessionHandoverRequest` to **World Server B**, transferring the
   player's session data (character ID, position, stats, inventory snapshot).
3. **World Server B** acknowledges and creates the session, spawning the entity in zone Y.
4. **World Server A** destroys the local session and emits a despawn to remaining zone X players.
5. **World Server A** signals the **Gateway** (via a control message) to re-bind the player's
   connection to World Server B.
6. The client receives a `ZoneTransitionResponse` and transitions its in-game state.

### Gateway Support
- Add a `zone_transition` control message type to `GatewayControlMessage`.
- The gateway handles `zone_transition` by updating the `WORLD_CLIENT_KEY` channel attribute
  to the new world server address.

## Dependencies
- `task-world-registry-integration.md` (must be complete)
- `task-world-entity-spawning.md` (must be complete)

## Acceptance Criteria
- A player crossing a zone boundary transitions to the new zone without disconnecting.
- No duplicate sessions exist after the handover completes.
- Other players in both zones receive correct spawn/despawn notifications.
- The gateway transparently routes the player's subsequent packets to the new world server.

## Sub-Tasks
This task has been broken into the following smaller tasks:
- **task-world-cross-zone-1-protocol.md** — ZoneTransitionRequest/Response DTOs and zone_transition gateway control command
- **task-world-cross-zone-2-handover-execution.md** — Server-side session transfer, entity lifecycle events, gateway re-bind
