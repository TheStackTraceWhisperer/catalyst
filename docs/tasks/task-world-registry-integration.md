# Task: World Registry Integration [PARTIALLY COMPLETED / UPDATED]

**Priority:** High (blocks horizontal world server scaling)  
**Area:** World Service / Lobby Service / Infrastructure
**Effort:** Medium (3-5 days)  

## Current Baseline Status
We have completed **Phase 1: Dynamic Gateway Routing & DNS Mapping**:
- The lobby service (`PlayRequestHandler`) now resolves the target world address dynamically based on the character's zone: `"world-service-" + id.currentZoneId() + ":35556"`.
- We mapped 9 individual Kubernetes zone services (`world-service-230` through `world-service-240`) to route traffic to the world deployment, eliminating static gateway world properties completely.
- The gateway's `RequestHandler` dynamically parses `worldAddress` from `play_success`, establishes the internal connection on the fly, caches it inside `dynamicWorldClients`, and binds it to the connection.

## Remaining Work: Phase 2 (Ideal State)
While Phase 1 fully exercises the dynamic gateway routing paths, the lobby service still hardcodes the zone-to-DNS mapping format. We need to implement a dynamic registry where multiple scaling world instances announce their zones.

### 1. World Service — Dynamic Zone Registration
- On startup, the world service registers the list of zones it is hosting into a shared registry.
- On shutdown, it deregisters its zones.
- Registry schema: `zoneId` -> `host:port` of the specific world server instance.

### 2. Registry Backend (Redis/DB)
- The registry must be backed by a shared datastore (Redis or a DB table) so the lobby service can read it without direct coupling to the world service instances.

### 3. Lobby Service — Registry Lookup on PLAY
- Instead of using the formatted DNS string `"world-service-" + zoneId`, the lobby queries the shared registry to find the `host:port` value registered for that `zoneId`.
- It returns this dynamically queried host/port in the `play_success` message.

## Acceptance Criteria (Phase 2)
- Starting two world service instances hosting different zones dynamically registers them in the shared datastore.
- The lobby service successfully routes players based on the registry lookup.
- Graceful shutdown of a world instance removes its entries, avoiding routing failures.
