# Task: World Registry Integration — Zone Registration on World Service Startup

**Priority:** High (blocks horizontal world server scaling)  
**Area:** World Service / Infrastructure  
**Effort:** Small (1-2 days)  
**Parent Task:** task-world-registry-integration.md  
**Depends On:** task-infra-redis-1-cluster-setup.md

## Purpose

Give each world service instance the ability to self-register the zones it is hosting into a shared Redis-backed registry on startup, and deregister them on shutdown. This is the producer side of the dynamic zone registry.

## What Needs to Happen

- Create a `ZoneRegistry` interface with:
  - `register(int zoneId, String hostPort)` — writes `zoneId → host:port` into Redis with a reasonable TTL (e.g. 30 seconds).
  - `deregister(int zoneId)` — removes the key.
  - `lookup(int zoneId)` — returns the `host:port` String or empty/null if not present.
- Create `RedisZoneRegistry` implementing the above using Lettuce/Jedis.
- In the world service startup lifecycle (`@PostConstruct` or `ServerStartupEvent`):
  - Determine the pod's own externally-reachable DNS name (e.g. from an env var `WORLD_SERVICE_HOST`) and port.
  - Call `registry.register(zoneId, host+":"+port)` for every zone this instance hosts.
- In the shutdown lifecycle (`@PreDestroy` or `ServerShutdownEvent`):
  - Call `registry.deregister(zoneId)` for each hosted zone.
- Set up a TTL heartbeat: re-register (refresh TTL) on a recurring timer so the entry survives brief Redis hiccups.

## Acceptance Criteria

- Starting a world-service pod writes its zone entries into Redis.
- Stopping the pod removes the entries (or they expire within the TTL window).
- Two world-service pods hosting different zone sets each register their own entries without overwriting the other.
