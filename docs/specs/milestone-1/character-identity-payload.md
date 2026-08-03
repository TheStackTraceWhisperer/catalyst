# Spec: Character Identity Payload

## Purpose

Define the character data returned to the client after `CHAR_SELECT` and `PLAY`.

## LSB Alignment

Character fields align to LSB's `chars`, `char_look`, `char_stats`, and `char_jobs` tables. See `docs/lsb-divergence.md` for high-level transport and schema divergence notes.

## Character Fields

### Appearance (from LSB `char_look`)

| Field | Type | Description |
|---|---|---|
| `race` | SMALLINT 1-8 | LSB encoding: 1=HumeM, 2=HumeF, 3=ElvaanM, 4=ElvaanF, 5=TaruM, 6=TaruF, 7=Mithra, 8=Galka |
| `size` | SMALLINT 0-2 | Body size: 0=Small, 1=Medium, 2=Large. Tarutaru forced=0, Galka forced=2 |
| `face` | SMALLINT 0-15 | Face 1-8 × A/B hair variant. Client displays as face 1-8 + A/B toggle |

### Job (from LSB `char_stats` / `char_jobs`)

| Field | Type | Description |
|---|---|---|
| `main_job` | SMALLINT 1-6 | Starting job: 1=WAR, 2=MNK, 3=WHM, 4=BLM, 5=RDM, 6=THF |

### Location (from LSB `chars`)

| Field | Type | Description |
|---|---|---|
| `nation` | SMALLINT 0-2 | Starting nation: 0=San d'Oria, 1=Bastok, 2=Windurst |
| `home_zone_id` | INT | Zone ID of home/respawn point |
| `home_x/y/z/rot` | REAL | Home spawn position and heading |
| `current_zone_id` | INT | Zone ID of current location |
| `current_x/y/z/rot` | REAL | Current position and heading |

### Identity

| Field | Type | Description |
|---|---|---|
| `id` | BIGINT | Server-assigned character ID |
| `account_id` | BIGINT | Owning account |
| `name` | VARCHAR(16) | Character name (3-15 letters A-Z, case-insensitive unique among non-deleted) |

## Flow

```
Client            Server
  |                  |
  |-- CHAR_SELECT -->|  load identity from DB, validate ownership
  |<-- CHAR_SELECT_OK|  returns: characterId, name, zones, position
  |                  |
  |---- PLAY ------->|  create accounts_sessions row, joinZone
  |<-- PLAY_OK ------|  returns: sessionId, zoneId, playersInZone, full identity
```

`CHAR_SELECT` does not create a session. `PLAY` creates the session and returns the full identity payload.

## Validation Rules (Server-Enforced at Character Creation)

- Name: `^[A-Za-z]{3,15}$`, case-insensitive uniqueness among non-deleted characters
- Race: 1-8 (LSB encoding); Galka forces size=2, Tarutaru forces size=0
- Face: 0-15
- Starting job: 1-6 (server clamps out-of-range values to 1-6, matching LSB behavior)
- Nation: 0-2; server randomly selects spawn zone within the nation's zone pool

## Fallback Rules

- If `current_zone_id` is unavailable, server falls back to `home_zone_id` / home position.
- Character identity is fully server-authoritative; client receives and displays only.

## Milestone 1 Done Criteria

- [x] Client receives and displays character identity in the character list
- [x] Server validates all creation fields before inserting
- [x] `PLAY_OK` contains `sessionId`, `zoneId`, and `playersInZone`
- [x] Client shows character race name and job name from server-returned fields
