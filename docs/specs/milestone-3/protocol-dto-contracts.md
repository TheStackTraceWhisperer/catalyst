# Spec: Protocol DTO Contracts

## Purpose

Replace string-key protocol field parsing in business logic with typed request/response DTO contracts shared in `ffxi-common`.

## Scope

- Login flow contracts
- Lobby contracts (`CHAR_LIST`, `CHAR_CREATE`, `CHAR_SELECT`, `CHAR_DELETE`)
- World contracts (`PLAY`, `PING`, `LOGOUT`)

## Design

- Add DTOs under `ffxi-common` for each request/response message.
- Keep `WireCodec`/`MessageFrame` as transport envelope.
- Add mappers at boundaries:
  - client gateway: `MessageFrame` ⇄ DTO
  - server dispatcher/handlers: DTO ⇄ `MessageFrame`
- Core state/handler logic consumes DTOs, not raw string maps.

## Rules

- Required fields are validated in mapper layer.
- Mapper errors produce explicit protocol errors (not silent defaults).
- No `"char" + i + "_..."` or similar key-generation logic outside mapping layer.

## Transition Plan

1. Introduce DTOs + mappers alongside existing frame usage.
2. Migrate one flow at a time (login → lobby → world).
3. Remove raw parsing from states/handlers once each flow is migrated.

## Milestone 3 Done Criteria

- [ ] Shared DTOs exist for login/lobby/world messages.
- [ ] Client state code uses typed contracts for migrated flows.
- [ ] Server handlers use typed contracts for migrated flows.
- [ ] String-key parsing is isolated to mapper/transport boundary code.
