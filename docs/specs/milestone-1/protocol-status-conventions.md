# Spec: Protocol Status and Error Conventions

## Purpose

Define the wire format and response semantics for Milestone 1 client/server messaging.

## Wire Format

All messages use a pipe-delimited text frame terminated with a newline:

```
TYPE|key=value|key=value|...\n
```

Values are URL-encoded (percent-encoding via `URLEncoder`/`URLDecoder`). Implemented in `catalyst.ffxi.common.net.WireCodec` and `MessageFrame`.

### Example

```
LOGIN|username=dev|password=dev\n
LOGIN_OK|code=OK|message=Authenticated|authToken=<uuid>|accountId=1\n
```

## Transport

Each request/response pair uses a dedicated bidirectional QUIC stream on a persistent `QuicChannel`. The client writes the request, shuts down output (half-close), and completes the `CompletableFuture` when a newline arrives in `channelRead`.

## Message Types

### Client → Server

| Type | Phase | Required Fields |
|---|---|---|
| `LOGIN` | Pre-auth | `username`, `password` |
| `CHAR_LIST` | Authenticated | `authToken` |
| `CHAR_CREATE` | Authenticated | `authToken`, `name`, `race`, `size`, `face`, `mainJob`, `nation` |
| `CHAR_SELECT` | Authenticated | `authToken`, `characterId` |
| `CHAR_DELETE` | Authenticated | `authToken`, `characterId` |
| `PLAY` | Authenticated | `authToken`, `characterId` |
| `PING` | In-game | `sessionId` |
| `LOGOUT` | In-game | `sessionId` |

### Server → Client (Success)

| Type | Returned by | Key Fields |
|---|---|---|
| `LOGIN_OK` | `LOGIN` | `code=OK`, `authToken`, `accountId` |
| `CHAR_LIST_OK` | `CHAR_LIST` | `count`, `char0_id`, `char0_name`, `char0_race`, `char0_raceName`, `char0_size`, `char0_face`, `char0_mainJob`, `char0_jobName`, `char0_nation`, `char0_zone` ... |
| `CHAR_CREATE_OK` | `CHAR_CREATE` | `characterId`, `name` |
| `CHAR_SELECT_OK` | `CHAR_SELECT` | `characterId`, `characterName`, `homeZoneId`, `currentZoneId`, `x`, `y`, `z`, `rot` |
| `CHAR_DELETE_OK` | `CHAR_DELETE` | `characterId` |
| `PLAY_OK` | `PLAY` | `sessionId`, `accountId`, `characterId`, `characterName`, `zoneId`, `playersInZone`, `homeZoneId`, `x`, `y`, `z`, `rot` |
| `PONG` | `PING` | `sessionId` |
| `BYE` | `LOGOUT` | `sessionId` |

### Server → Client (Error)

Error responses use type `LOGIN_ERR` (for LOGIN failures) or `ERROR` (for all other failures).

| Type | Code field values |
|---|---|
| `LOGIN_ERR` | `INVALID_CREDENTIALS`, `ACCOUNT_DISABLED`, `SERVER_ERROR` |
| `ERROR` | `UNAUTHORIZED`, `INVALID_CHARACTER`, `CHARACTER_NOT_FOUND`, `NAME_ALREADY_TAKEN`, `INVALID_NAME`, `INVALID_RACE`, `INVALID_SIZE`, `INVALID_FACE`, `INVALID_NATION`, `CHARACTER_ACTIVE`, `ALREADY_ONLINE`, `SESSION_NOT_FOUND`, `SERVER_ERROR`, `UNKNOWN_REQUEST` |

All error frames include a `code` and `message` field. `message` is safe for display and does not expose internals.

## Rules

- Every request receives exactly one response.
- Unknown message types return `ERROR code=UNKNOWN_REQUEST`.
- Auth failures never reveal whether the username exists.
- No request ID or protocol version field in M1 (forward compatibility via message type versioning).

## Milestone 1 Done Criteria

- [x] Client and server encode/decode all messages using `WireCodec`
- [x] All message types above are implemented and tested end-to-end
- [x] Error responses include `code` and `message` on all failure paths
