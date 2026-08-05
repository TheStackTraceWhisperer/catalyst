# Milestone 5: Automated E2E and CI Gating

## Goal

Establish automated integration testing and continuous integration pipelines to ensure code stability, prevent regressions, and enforce project quality standards.

## Scope

### 1. Automated end-to-end validation

- Create/stabilize an automated E2E flow covering the primary happy-path game client cycle:
  - Login → Create Character → List Verification → Select Character → Enter Game (Play) → Ping/Pong Validation → Logout.
- Build local scripts to easily run this E2E test suite locally against containerized infrastructure.

### 2. CI/CD Pipeline

- Design and implement a GitHub Actions (or similar) workflow to run on pull requests and commits.
- Ensure that the E2E verification test runner executes successfully in the CI environment.
- Enforce that the automated E2E validation must pass as a gating check before code merges are allowed.

## Deliverables

- Automated E2E test runner (harness + execution script).
- Fully integrated CI workflow configuration file (`.github/workflows/e2e-ci.yml`).
- Gating and logs archive mechanism for test runs.

## Acceptance Criteria

- [x] Automated E2E flow covers: login → character creation → character selection → play → ping/pong → logout.
- [x] E2E tests run successfully via a single command or script locally.
- [x] CI workflow automatically executes the E2E validation suite on pull requests.
- [x] CI workflow acts as a gating status check (passing is required for merge).

---

## Milestone 5 Status: CLOSED

**Closed:** 2026-08-05

Milestone 5 is formally closed. The E2E integration test harness has been extracted to a standalone module (`catalyst-tests`), and the GitHub Actions workflow (`.github/workflows/e2e-ci.yml`) is integrated to compile, build, boot, and run tests against Postgres on push/pull requests.
