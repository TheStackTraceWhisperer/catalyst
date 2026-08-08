# Task: World Entity Spawning — Player Spawn & Despawn on PLAY/LOGOUT

**Priority:** Medium (core gameplay prerequisite)  
**Area:** World Service / Game Simulation  
**Effort:** Small (1-2 days)  
**Parent Task:** task-world-entity-spawning.md  
**Depends On:** task-world-entity-spawning-1-entity-model.md

## Purpose

Wire the `ZoneState` into the existing PLAY and LOGOUT flows so that entering and leaving the world correctly registers and removes a player entity, and other connected players in the same zone are notified.

## What Needs to Happen

- In `WorldPlayRequestHandler.handle(...)`:
  1. After session creation, construct a `ZoneEntity` for the player at their `x, y, z, rot` and `zoneId`.
  2. Call `zoneManager.getZoneState(zoneId).addEntity(entity)`.
  3. Broadcast a `SpawnNotification { long entityId, EntityType type, float x, y, z, rot }` DTO to all other active sessions in the zone (use the existing `ZoneMessageDispatcher` or equivalent broadcast channel).
  4. Send a `SpawnNotification` for every *existing* entity in the zone to the newly joining player, so they see who is already present.

- In `WorldLogoutRequestHandler.handle(...)`:
  1. Look up the player's entity ID from their session.
  2. Call `zoneManager.getZoneState(zoneId).removeEntity(entityId)`.
  3. Broadcast a `DespawnNotification { long entityId }` to all remaining sessions in the zone.

- Define `SpawnNotification` and `DespawnNotification` DTOs in `common-dto` under `catalyst.common.dto.world`.

## Acceptance Criteria

- Two clients in the same zone each receive the other's `SpawnNotification` on entry.
- A client leaving receives a `LogoutResponse`; remaining clients receive a `DespawnNotification`.
- E2E test harness still passes with the new notification DTOs in the pipeline.
