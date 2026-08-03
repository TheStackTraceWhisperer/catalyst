# Spec: Zone Routing Skeleton

## Purpose

Provide minimal zone/session attachment behavior without full zone simulation.

## Implementation

Zone management is in-memory within `ServerMain` using two `ConcurrentHashMap`s:

```java
ConcurrentHashMap<String, Integer> sessionZones;       // sessionId → zoneId
ConcurrentHashMap<Integer, AtomicInteger> zonePopulation; // zoneId → player count
```

## API (Implemented)

| Operation | Triggered by | Behavior |
|---|---|---|
| `joinZone(sessionId, zoneId)` | `PLAY` | Adds sessionId→zoneId mapping, increments zone counter, logs `ZONE_ENTER` |
| `leaveZone(sessionId, zoneId)` | `LOGOUT`, session timeout cleanup | Removes mapping, decrements zone counter, logs `ZONE_LEAVE` |

`moveSession` (zone transfer) is not implemented in Milestone 1.

## PLAY_OK Response

On successful `PLAY`, the response includes:

- `zoneId` — the character's current zone
- `playersInZone` — current player count in that zone (post-join)

## Observability

All zone operations are logged at INFO level:

```
ZONE_ENTER zone=230 session=<uuid> playersInZone=1
ZONE_LEAVE zone=230 session=<uuid> playersInZone=0
```

## Zone Population Lifecycle

1. `PLAY` → `joinZone` → counter incremented
2. `LOGOUT` → `leaveZone` → counter decremented
3. Session timeout cleanup → `leaveZone` called before row deletion → counter decremented
4. When zone population reaches 0, the `AtomicInteger` entry is removed from the map

## Starting Zone Assignment

The zone a character starts in is chosen randomly from the nation's zone pool at character creation time. See [Account/Session Record Structures](./account-session-record-structures.md) for the full zone pool table.

## Milestone 1 Done Criteria

- [x] `joinZone` is called on `PLAY`; `leaveZone` on `LOGOUT` and timeout cleanup
- [x] `playersInZone` is returned in `PLAY_OK`
- [x] Zone enter/leave events are logged
- [x] Zone counter correctly handles concurrent sessions (AtomicInteger)
