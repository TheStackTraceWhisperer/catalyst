# Task: World Entity Spawning & Spatial Tracking

**Priority:** Medium (core gameplay prerequisite)  
**Area:** World Service / Game Simulation
**Effort:** Large (1-2 weeks)  

## Purpose

Before any gameplay interactions (combat, movement, NPC dialogue) can function, the world service
needs a spatial model: a data structure that knows which entities exist in a zone, where they are,
and what kind they are (player, monster, NPC, object).

## What Needs to Happen

### Entity Model
- Define an `Entity` or `ZoneEntity` class capturing: `entityId`, `type` (PLAYER / MONSTER / NPC),
  `zoneId`, `x`, `y`, `z`, `rot`, and any type-specific state (e.g., HP for combat-capable entities).

### Zone Entity Registry
- Each active zone maintains a spatial map of entities: `entityId → Entity`.
- The `ZoneManager` (or a new `ZoneState` class) owns this map.
- Mutations to entity positions happen inside the `ZoneMessageDispatcher` 10Hz tick loop to
  prevent race conditions.

### Player Spawn on PLAY
- When `WorldPlayRequestHandler` creates a new session, it must:
  1. Instantiate a player entity from the character's `x, y, z, rot` and `zoneId`.
  2. Register it in the zone entity registry.
  3. Broadcast a spawn notification to all other players in the zone.

### Player Despawn on LOGOUT
- When `WorldLogoutRequestHandler` processes a logout, it must:
  1. Remove the player entity from the zone entity registry.
  2. Broadcast a despawn notification to remaining players in the zone.

### Movement Packet (new DTO)
- Define `MoveRequest { float x, y, z, rot }` in `common-dto`.
- Add `MoveRequestHandler` in the world service that updates the entity's position and broadcasts
  a `MoveUpdate { entityId, x, y, z, rot }` to other zone members.
- Client sends `MoveRequest` as a fire-and-forget `sendAsync(...)` call.

## Acceptance Criteria
- Two clients in the same zone each receive the other's spawn notification on entry.
- Movement updates from one client appear in the zone entity state on the server.
- Logout removes the entity and notifies remaining players.
