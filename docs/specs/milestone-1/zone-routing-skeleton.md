# Spec: Zone Routing Skeleton

## Purpose

Provide minimal zone/session attachment behavior without full zone simulation.

## Functional Requirements

- Maintain a zone registry keyed by `zoneId`.
- Attach authenticated session to one zone context (`homeZoneId` initially).
- Support basic session handoff API:
  - `attachSession(sessionId, zoneId)`
  - `detachSession(sessionId, zoneId)`
  - `moveSession(sessionId, fromZoneId, toZoneId)` (stubbed path acceptable)

## Scope Constraints

- No NPC/AI/combat logic required.
- No full world tick beyond what is needed to hold session membership.

## Observability

- Emit structured logs on attach/detach/move.
- Expose session counts per zone for debug visibility.

## Milestone 1 Done Criteria

- Session is attached to a zone context after auth + identity resolution.
- Detach/cleanup happens on disconnect/timeout.

