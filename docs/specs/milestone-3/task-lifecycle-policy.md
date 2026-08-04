# Spec: Task Lifecycle and Scheduling Policy

## Purpose

Standardize task lifecycle behavior (submit, cancel, timeout, completion, failure) and eliminate one-off executor policy decisions across modules.

## Task Model

`TaskHandle<T>` should provide:

- immutable task id
- status (`PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `TIMED_OUT`)
- completion future or await primitive
- error/result access
- cancellation API

## Policy

- Default to bounded tasks with explicit timeout where operation class is known.
- Cancellation should be cooperative and visible to callers.
- Failures propagate to caller callbacks and logs; no swallowing.
- Replace feature-owned `ScheduledExecutorService` usage with shared scheduling abstractions where practical.

## Migration Targets

- Client keepalive scheduling path.
- Any background loading/parsing in viewer/client.
- Server periodic maintenance tasks where shared policy improves observability.

## Observability

- Emit task lifecycle events to logs at debug/info as appropriate.
- Include task id + operation name for traceability.

## Milestone 3 Done Criteria

- [ ] `TaskHandle` contract exists and is used by scheduler API.
- [ ] Timeout and cancellation paths are implemented and tested.
- [ ] Existing ad-hoc scheduling call sites are migrated or wrapped by shared policy.
