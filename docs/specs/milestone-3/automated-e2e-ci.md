# Spec: Automated E2E and CI Gating

## Purpose

Codify manual gameplay validation into repeatable automation that runs in CI and blocks regressions.

## E2E Scenario

Primary happy-path flow:

1. Start server dependencies.
2. Boot server.
3. Run client/protocol harness actions:
   - LOGIN
   - CHAR_CREATE
   - CHAR_LIST verification
   - CHAR_SELECT
   - PLAY
   - PING/PONG validation
   - LOGOUT
4. Assert expected responses and state transitions.
5. Teardown cleanly.

## Test Harness Strategy

- Prefer protocol-level harness first (fast, deterministic, headless-capable).
- Add GUI-driven smoke tests later where necessary.
- Reuse helper scripts for environment bootstrapping.

## CI Requirements

- Run on every PR.
- Surface logs/artifacts on failure (server/client logs, protocol transcript).
- Enforce non-optional status check for merge.

## Reliability Considerations

- Isolate test DB/schema per run.
- Use deterministic test users/characters with cleanup.
- Set explicit command timeouts and fail fast on startup issues.

## Milestone 3 Done Criteria

- [ ] E2E harness exists and runs locally in one command.
- [ ] CI workflow executes E2E harness on PRs.
- [ ] Failing E2E blocks merge.
- [ ] Failure output includes enough diagnostics to triage quickly.
