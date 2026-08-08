# Task: World Entity Spawning — Movement Packets & Broadcast

**Priority:** Medium (core gameplay prerequisite)  
**Area:** World Service / Game Simulation  
**Effort:** Small (1-2 days)  
**Parent Task:** task-world-entity-spawning.md  
**Depends On:** task-world-entity-spawning-2-spawn-despawn.md

## Purpose

Allow clients to send movement updates that are reflected in the server's zone state and broadcast to all other players in the same zone, enabling basic real-time positional synchronisation.

## What Needs to Happen

- Define `MoveRequest { float x, float y, float z, float rot }` in `catalyst.common.dto.world` (implements `WorldGatewayMessage`).
- Define `MoveUpdate { long entityId, float x, float y, float z, float rot }` in the same package (broadcast outbound, does NOT need to implement a gateway message interface).
- Create `MoveRequestHandler` in the world service:
  - Validates the session is active.
  - Updates the `ZoneEntity` position inside `ZoneState` (ensure this runs within the `ZoneMessageDispatcher` tick to avoid race conditions).
  - Broadcasts `MoveUpdate` to all other sessions in the zone.
- Wire `MoveRequestHandler` into the world service handler dispatch map.
- Client sends `MoveRequest` as a fire-and-forget `sendAsync(...)` call (no blocking wait for response).
- Register `MoveRequest` and `MoveUpdate` in the relevant Fory class registration lists.

## Acceptance Criteria

- A `MoveRequest` from client A causes client B (same zone) to receive a `MoveUpdate` with client A's entity ID and new position.
- The server-side `ZoneEntity` position is updated after handling the request.
- Movement at high frequency does not cause thread-safety issues inside `ZoneState`.
