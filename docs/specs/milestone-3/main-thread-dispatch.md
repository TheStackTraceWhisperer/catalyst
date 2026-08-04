# Spec: Main-thread Dispatch Primitive

## Purpose

Guarantee a single, explicit path for executing render-thread-safe callbacks from background work.

## Design

- Expose `runOnMainThread(Runnable)` on scheduler service.
- Queue order is FIFO.
- Drain strategy: process full queue each frame; log and continue on callback errors.

## Safety Constraints

- No direct state mutation from background threads for UI-bound objects.
- No silent callback drop: failures are logged with context.
- Queue operations must be lock-free or low-contention (`ConcurrentLinkedQueue`).

## Usage Pattern

1. Background task computes result.
2. Schedules success/error callback via `runOnMainThread`.
3. Engine drains queue in frame loop.

## Milestone 3 Done Criteria

- [ ] Shared `runOnMainThread` API is available to client code.
- [ ] Foreground callback execution is deterministic and frame-driven.
- [ ] Callback exceptions do not crash the loop; they are surfaced in logs/debug output.
