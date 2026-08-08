# Task: World Entity Spawning — Entity Model & Zone State

**Priority:** Medium (core gameplay prerequisite)  
**Area:** World Service / Game Simulation  
**Effort:** Small (1-2 days)  
**Parent Task:** task-world-entity-spawning.md

## Purpose

Define the core `Entity` data model and the in-memory `ZoneState` that tracks which entities exist in each active zone. This is the foundational data layer everything else in entity spawning builds on.

## What Needs to Happen

- Define a `ZoneEntity` record (or class) capturing:
  - `long entityId` — server-assigned unique ID within the zone.
  - `EntityType type` — enum: `PLAYER`, `MONSTER`, `NPC`.
  - `int zoneId`
  - `float x, y, z, rot`
  - `int hp`, `int maxHp` — for combat-capable entities (can default to 0 for non-combat initially).
- Create `ZoneState`:
  - Holds a `ConcurrentHashMap<Long, ZoneEntity>` keyed by `entityId`.
  - `addEntity(ZoneEntity)`, `removeEntity(long entityId)`, `getAll()` methods.
  - Entity ID assignment: simple atomic long counter per zone is sufficient initially.
- `ZoneManager` stores a `Map<Integer, ZoneState>` keyed by zone ID.
  - On zone activation (first player entering), create a `ZoneState` if absent.
  - Expose `getZoneState(int zoneId)`.

## Acceptance Criteria

- `ZoneState` correctly tracks add/remove operations without data races.
- `ZoneManager` can return the correct `ZoneState` for any active zone ID.
- Unit tests cover: add entity, remove entity, concurrent add+remove on the same zone state.
