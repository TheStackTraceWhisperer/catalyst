# Milestone 3: Protocol Contracts & Test Automation

## Planned Tasks

- [ ] Convert login/lobby/world message contracts to shared typed DTOs in `ffxi-common` (request/response models), with wire mapping isolated at transport boundaries.
- [ ] Remove remaining string-key protocol parsing from state/handler logic; consume typed DTOs end-to-end.
- [ ] Add an automated end-to-end test workflow that runs the core flow: login → create character → select → play → ping → logout.
- [ ] Run the automated E2E flow in CI so regressions are caught without manual gameplay validation.

## Deferred

- Apache Fory migration is intentionally deferred until after DTO contract stabilization.
