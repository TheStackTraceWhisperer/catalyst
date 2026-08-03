# Spec: Account/Auth Model

## Purpose

Provide the minimum authentication system needed for Milestone 1 login/session establishment.

## Functional Requirements

- Lookup account by username/login ID.
- Verify credential using **Argon2id** (never plaintext comparison).
- Validate account status and ban state before allowing auth success.
- Return explicit auth result codes:
  - `AUTH_SUCCESS`
  - `AUTH_INVALID_CREDENTIALS`
  - `AUTH_ACCOUNT_DISABLED`
  - `AUTH_ACCOUNT_BANNED`
  - `AUTH_RATE_LIMITED`
  - `AUTH_SERVER_ERROR`
- Prevent session creation unless auth succeeds.

## Data Contract (Minimum)

- `accountId` (stable internal ID)
- `loginName`
- `passwordHash`
- `status` (`ACTIVE` | `DISABLED`)
- `banState` (derived from ban records)
- `createdAt`, `updatedAt`

## Password Hashing Standard

- Use **Argon2id** for password hashing and verification.
- Store password data in **PHC string format** (algorithm + parameters + salt + hash).
- Use a unique cryptographically secure random salt per password.
- Argon2id cost parameters (memory/time/parallelism) must be configurable by environment.

## Non-Functional Requirements

- Password verification is constant-time where feasible.
- Auth events are auditable (success/failure reason, source endpoint/session ID).
- Failure paths must not leak whether username exists.

## Milestone 1 Done Criteria

- Server validates login credentials and returns canonical auth result.
- Only `AUTH_SUCCESS` can transition session into authenticated state.
