# Task: World Registry Integration

**Priority:** High (blocks horizontal world server scaling)  
**Area:** World Service / Lobby Service / Infrastructure

## Problem

Currently, the lobby service's `PlayRequestHandler` returns a hardcoded `worldAddress` of
`"DEFAULT"`, and the gateway falls back to its statically configured `CATALYST_GATEWAY_WORLDHOST`
and `CATALYST_GATEWAY_WORLDPORT` environment variables. There is no dynamic mechanism for:

- Multiple world server instances to announce which zones they are hosting.
- The lobby service to look up the correct world server for a given `zoneId` at play time.
- The gateway to dynamically route to the right world server per player.

## What Needs to Happen

### World Service — Zone Registration
- On startup (`@PostConstruct` or `ApplicationEventListener<ServerStartupEvent>`), the world
  service registers all zones it is hosting into a shared registry.
- On shutdown, it deregisters its zones.
- Registry key: `zoneId`, value: `host:port` of the world server instance.

### Registry Backend (Redis)
- See `task-infra-redis.md`. The registry must be backed by Redis so the lobby service and gateway
  can read it without coupling to the world service directly.
- For local development (pre-Redis), a simple in-memory singleton can be used as a placeholder.

### Lobby Service — Zone Lookup on PLAY
- `PlayRequestHandler` resolves the `zoneId` of the character's `homeZoneId`.
- Looks up the registry for the assigned world server address.
- Contacts that world server to create the session and confirm the `PlayResponse`.
- Returns the resolved `worldAddress` in the response so the gateway can route correctly.

### Gateway — Dynamic World Routing
- The gateway's `handleStateTransitions(...)` already supports dynamic `worldAddress` parsing from
  the `play_success` control message and uses `dynamicWorldClients` to cache connections.
- This path becomes fully exercised once the lobby service starts returning real addresses.

## Acceptance Criteria
- Starting two world service instances hosting different zones causes both to appear in the registry.
- A `PLAY` request for a character in zone A routes to world server A, not world server B.
- Stopping a world server instance removes its zone registrations from the registry.
