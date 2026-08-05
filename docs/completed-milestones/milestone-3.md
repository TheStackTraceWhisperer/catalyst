# Milestone 3: Kernel Concurrency and Typed Contracts

## Goal

Strengthen the post-M2 kernel by introducing a first-class background task model and completing protocol contract typing.

M3 is focused on core infrastructure, not new gameplay systems.

## Scope

### 1. Virtual-thread task scheduling kernel

- Add a shared task scheduler service in `common` based on Java virtual threads.
- Support background task submission with foreground (main-thread) callback execution.
- Process foreground callbacks during the engine loop so OpenGL/UI operations remain main-thread-safe.
- Provide explicit lifecycle (`start`, `shutdown`) and robust error propagation.

Reference pattern: `xim-analysis/viewer/TaskScheduler`.

### 2. Main-thread dispatch primitive

- Expose a reusable API for code that must marshal work back to the render thread.
- Ensure feature code does not directly manage thread handoff logic.

### 3. Task lifecycle and policy

- Introduce task handles (status/result/error/cancel).
- Support bounded execution (timeouts/cancellation where appropriate).
- Define one shared scheduling policy instead of ad-hoc executors in feature classes.

### 4. Protocol DTO conversion

- Convert login/lobby/world request/response contracts to typed DTOs in `common`.
- Isolate wire-to-DTO mapping at transport edges (gateway/handler boundaries).
- Remove string-key protocol parsing from state/handler logic.

## Phased Plan

### Phase A — Concurrency foundation

1. Add `TaskSchedulerService` in `common`.
2. Add foreground queue processing in engine tick.
3. Define APIs: submit background task, queue main-thread callback, cancel/timeout.
4. Migrate existing scheduled/background work to shared abstractions where feasible.

### Phase B — Contract migration

1. Introduce DTOs for current protocol surface.
2. Refactor client/server flow code to consume typed contracts.

## Deliverables

- Shared virtual-thread scheduler service in `common`.
- Main-thread callback dispatch API.
- Shared DTO contract set for login/lobby/world.
- Refactored client/server flows using DTOs (no direct string-key protocol access in states/handlers).

## Acceptance Criteria

- [x] `common` exposes a reusable virtual-thread scheduler service.
- [x] Background task completion callbacks execute on the main/render thread through a shared dispatch path.
- [x] Task execution supports cancellation and explicit failure reporting (no silent failure paths).
- [x] Ad-hoc executor usage in feature code is removed or routed through shared scheduler abstractions.
- [x] Login/lobby/world contracts are represented by typed DTOs in `common`.
- [x] String-key protocol parsing is removed from client state classes and server handler classes.

## Out of Scope

- New gameplay features (combat, movement systems, rendering features).
- Large-scale server topology changes.
- Binary protocol migration.
- Automated end-to-end validation in CI (split into Milestone 5).

## Deferred

- Apache Fory migration is deferred until after DTO contract stabilization and E2E coverage is in place.



---

## Milestone 3 Status: CLOSED

**Closed:** 2026-08-05

Milestone 3 is formally closed. Concurrency foundations and Protocol DTO conversions are complete. The E2E automation and CI workflow setup has been moved to Milestone 5.
