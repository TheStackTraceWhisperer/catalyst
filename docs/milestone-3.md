# Milestone 3: Kernel Concurrency, Typed Contracts, and Automation

## Goal

Strengthen the post-M2 kernel by introducing a first-class background task model, completing protocol contract typing, and adding automated end-to-end validation in CI.

M3 is focused on core infrastructure, not new gameplay systems.

## Scope

### 1. Virtual-thread task scheduling kernel

- Add a shared task scheduler service in `ffxi-common` based on Java virtual threads.
- Support background task submission with foreground (main-thread) callback execution.
- Process foreground callbacks during the engine loop so OpenGL/UI operations remain main-thread-safe.
- Provide explicit lifecycle (`start`, `shutdown`) and robust error propagation.

Reference pattern: `ffxi-xim-analysis/ffxi-viewer/TaskScheduler`.

### 2. Main-thread dispatch primitive

- Expose a reusable API for code that must marshal work back to the render thread.
- Ensure feature code does not directly manage thread handoff logic.

### 3. Task lifecycle and policy

- Introduce task handles (status/result/error/cancel).
- Support bounded execution (timeouts/cancellation where appropriate).
- Define one shared scheduling policy instead of ad-hoc executors in feature classes.

### 4. Protocol DTO conversion

- Convert login/lobby/world request/response contracts to typed DTOs in `ffxi-common`.
- Isolate wire-to-DTO mapping at transport edges (gateway/handler boundaries).
- Remove string-key protocol parsing from state/handler logic.

### 5. Automated end-to-end validation

- Add automated E2E flow for: login → create character → select → play → ping → logout.
- Run E2E in CI to gate regressions.

## Phased Plan

### Phase A — Concurrency foundation

1. Add `TaskSchedulerService` in `ffxi-common`.
2. Add foreground queue processing in engine tick.
3. Define APIs: submit background task, queue main-thread callback, cancel/timeout.
4. Migrate existing scheduled/background work to shared abstractions where feasible.

### Phase B — Contract migration and automation

1. Introduce DTOs for current protocol surface.
2. Refactor client/server flow code to consume typed contracts.
3. Implement automated E2E harness.
4. Add CI workflow and enforce pass criteria.

## Deliverables

- Shared virtual-thread scheduler service in `ffxi-common`.
- Main-thread callback dispatch API.
- Shared DTO contract set for login/lobby/world.
- Refactored client/server flows using DTOs (no direct string-key protocol access in states/handlers).
- Automated E2E test runner.
- CI workflow executing E2E checks.

## Acceptance Criteria

- [ ] `ffxi-common` exposes a reusable virtual-thread scheduler service.
- [ ] Background task completion callbacks execute on the main/render thread through a shared dispatch path.
- [ ] Task execution supports cancellation and explicit failure reporting (no silent failure paths).
- [ ] Ad-hoc executor usage in feature code is removed or routed through shared scheduler abstractions.
- [ ] Login/lobby/world contracts are represented by typed DTOs in `ffxi-common`.
- [ ] String-key protocol parsing is removed from client state classes and server handler classes.
- [ ] Automated E2E flow covers login → create → select → play → ping → logout.
- [ ] E2E flow runs in CI and is required for milestone completion.

## Out of Scope

- New gameplay features (combat, movement systems, rendering features).
- Large-scale server topology changes.
- Binary protocol migration.

## Deferred

- Apache Fory migration is deferred until after DTO contract stabilization and E2E coverage is in place.

## Specifications

- [Virtual-thread Task Scheduler Kernel](./specs/milestone-3/task-scheduler-kernel.md)
- [Main-thread Dispatch Primitive](./specs/milestone-3/main-thread-dispatch.md)
- [Task Lifecycle and Scheduling Policy](./specs/milestone-3/task-lifecycle-policy.md)
- [Protocol DTO Contracts](./specs/milestone-3/protocol-dto-contracts.md)
- [Automated E2E and CI Gating](./specs/milestone-3/automated-e2e-ci.md)
