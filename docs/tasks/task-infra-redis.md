# Task: Redis Integration

**Priority:** Later  
**Area:** Infrastructure / Caching / Session Management
**Effort:** Medium (3-5 days)  

## Purpose

Introduce Redis as a shared in-memory data store to replace the current in-process, ephemeral
state that would not survive a pod restart or horizontal scale-out.

## Use Cases

### 1. Session Management
- Currently, active player sessions are stored in PostgreSQL only. There is no fast lookup for
  validating an auth token or session ID on the hot path.
- Redis should hold the active session map: `sessionId → { accountId, characterId, zoneId, worldAddress }`.
- The gateway can validate tokens against Redis without going to Postgres on every connection.

### 2. World Registry
- The lobby service needs to know which world server instance is hosting a given `zoneId` when
  routing a `PLAY` request.
- The world registry should be a Redis hash: `zoneId → worldServerAddress` (host:port).
- World server instances register themselves in Redis on startup and deregister on shutdown.
- This enables horizontal world server scaling without static configuration.

### 3. Caching
- Frequently-read, rarely-changing data (e.g., zone metadata, race/job/nation tables, NPC
  spawn tables) can be cached in Redis to avoid repeated Postgres round-trips on the hot path.

## What Needs to Happen
- Add Redis (e.g., Valkey or Redis 7+) to the k3d Kubernetes manifests.
- Add `micronaut-redis-lettuce` dependency to the relevant services.
- Implement a `SessionRepository` backed by Redis in the world service.
- Implement a `WorldRegistry` backed by Redis in the lobby service and world service.
- Update the gateway's `play_success` handler to look up the world address from Redis rather than
  static configuration.

## Acceptance Criteria
- World service registers its hosted zones in Redis on startup.
- Lobby service reads the world address from Redis on `PLAY` requests.
- Active sessions survive a lobby service pod restart (they live in Redis, not in-process).
- The gateway can validate a session ID against Redis without touching Postgres.
