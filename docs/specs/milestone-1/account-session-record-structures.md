# Spec: Account/Session Record Structures

## Purpose

Define Milestone 1 account-centric records with parity to LSB session-management behavior (no double logins, timeout cleanup, auth/session traceability).

## LSB Reference Tables (Source)

- `accounts`
- `accounts_banned`
- `accounts_sessions`
- `account_ip_record`
- `accounts_totp`

## Required Records for Milestone 1

### 1) `accounts`

Minimum fields:

- `id` (account ID, primary key)
- `login`
- `passwordHash`
- `status` (active/disabled)
- `privilegeLevel`
- `createdAt`
- `updatedAt`

### 2) `accounts_banned`

Minimum fields:

- `accountId` (primary key / foreign key to `accounts`)
- `bannedAt`
- `unbanAt`
- `banComment`

### 3) `accounts_sessions`

Minimum fields:

- `accountId` (unique)
- `characterId` (primary key or unique)
- `sessionKey`
- `serverAddress`
- `serverPort`
- `clientAddress`
- `clientPort`
- `versionMismatch`
- `lastZoneOutAt` (or equivalent transition timestamp)
- `lastSeenAt` (heartbeat/activity timestamp)

Required constraints:

- **Unique active session per account** (`UNIQUE(accountId)`)
- **Unique active session per character** (`UNIQUE(characterId)` or `PRIMARY KEY(characterId)`)

### 4) `account_ip_record`

Minimum fields:

- `loginTime`
- `accountId`
- `characterId`
- `clientIp`

### 5) `accounts_totp` (optional in M1, but schema-reserved)

Minimum fields:

- `accountId` (primary key)
- `secret`
- `recoveryCode`
- `validated`

## Session Management Rules Backed by Records

- Successful login must create/refresh one `accounts_sessions` row.
- New login attempt for an already-active account must either:
  - reject with explicit status, or
  - atomically invalidate/close old session and replace it.
- Timeout/disconnect must remove or invalidate active session record deterministically.
- Session records are server-authoritative and cannot be created by unauthenticated requests.

## Milestone 1 Done Criteria

- Equivalent account/session record set exists in schema.
- Uniqueness constraints prevent double-login ambiguity.
- Session rows are created, updated, and cleaned automatically for connect/auth/disconnect/timeout paths.

