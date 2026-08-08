# Task: Redis Integration — Session Store Migration

**Priority:** Later  
**Area:** Infrastructure / Caching / Session Management  
**Effort:** Small (1-2 days)  
**Parent Task:** task-infra-redis.md  
**Depends On:** task-infra-redis-1-cluster-setup.md

## Purpose

Replace the current in-process, ephemeral `ConcurrentHashMap`-based session stores in the world service and lobby service with Redis-backed equivalents so that sessions survive pod restarts and are visible across service replicas.

## What Needs to Happen

- Create a `RedisSessionStore` implementation that wraps the Lettuce/Jedis client:
  - `put(sessionId, sessionData, ttlSeconds)` — write session JSON/binary to Redis with TTL.
  - `get(sessionId)` — retrieve and deserialize from Redis.
  - `delete(sessionId)` — explicit removal on logout or expiry.
- Replace the in-process `Map<String, Session>` references in:
  - `WorldSessionManager` (world service) — active play sessions.
  - `LobbyService` or equivalent (lobby service) — character-select state if applicable.
- Ensure TTL is set conservatively (e.g. 10 minutes) so orphaned sessions are cleaned up automatically even without a `logout_success` event (complements `task-world-session-expiry.md`).

## Acceptance Criteria

- Restarting the world-service pod does not destroy active sessions.
- Two replicas of the world service can both read the same session by ID.
- Sessions that are never explicitly deleted expire from Redis after the configured TTL.
