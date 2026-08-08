# Task: Cross-Zone Handover — Server-Side Handover Execution

**Priority:** Low (depends on World Registry and Entity Spawning)  
**Area:** World Service / Gateway  
**Effort:** Small (1-2 days)  
**Parent Task:** task-world-cross-zone-handover.md  
**Depends On:** task-world-cross-zone-1-protocol.md, task-world-registry-1-zone-registration.md, task-world-entity-spawning-2-spawn-despawn.md

## Purpose

Implement the actual multi-step handover execution inside the world service: session transfer from World Server A to World Server B, entity lifecycle events in both zones, and the gateway re-bind signal.

## What Needs to Happen

1. **World Server A** (source zone):
   - Receives a `ZoneTransitionRequest` from the client (via the gateway).
   - Looks up World Server B's address in the zone registry for `targetZoneId`.
   - Sends a `SessionHandoverRequest` to World Server B over an internal QUIC connection.
   - Waits for acknowledgment from World Server B.
   - On success: despawns the player entity from zone X, broadcasts despawn to remaining zone X players.
   - Emits a `zone_transition` `GatewayControlMessage` containing World Server B's `host:port`.
   - Destroys the local session.

2. **World Server B** (destination zone):
   - Receives the `SessionHandoverRequest`.
   - Creates a new session for the player in zone Y.
   - Spawns the player entity at the transition coordinates.
   - Broadcasts spawn to existing zone Y players.
   - Acknowledges the handover to World Server A.

3. **Gateway**:
   - Receives the `zone_transition` control message.
   - Updates `WORLD_CLIENT_KEY` to the new BackendClient for World Server B.
   - All subsequent packets from the client route transparently to World Server B.

## Acceptance Criteria

- A player crossing a zone boundary transitions without disconnecting.
- No duplicate sessions exist after handover.
- Other players in both zones receive correct spawn/despawn notifications.
- The gateway transparently routes to the new world server after the transition.
