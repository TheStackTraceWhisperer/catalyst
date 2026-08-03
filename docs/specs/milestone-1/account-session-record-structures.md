# Spec: Account/Session Record Structures

## Purpose

Define the database schema backing account, character, and session lifecycle for Milestone 1.

## Implemented Tables

### `accounts`

```sql
CREATE TABLE accounts (
  id            BIGSERIAL PRIMARY KEY,
  username      VARCHAR(64) NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  status        VARCHAR(16) NOT NULL DEFAULT 'active',
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### `accounts_sessions`

Active game sessions only. One row per logged-in character. Created by `PLAY`, deleted by `LOGOUT` or timeout cleanup.

```sql
CREATE TABLE accounts_sessions (
  session_id   UUID PRIMARY KEY,
  account_id   BIGINT NOT NULL REFERENCES accounts(id),
  character_id BIGINT NOT NULL,
  zone_id      INT NOT NULL DEFAULT 0,
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (account_id),    -- one active session per account
  UNIQUE (character_id)   -- one active session per character
);
```

### `characters`

LSB-aligned character data. Soft delete via `deleted_at`.

```sql
CREATE TABLE characters (
  id              BIGSERIAL PRIMARY KEY,
  account_id      BIGINT NOT NULL REFERENCES accounts(id),
  name            VARCHAR(16) NOT NULL,
  race            SMALLINT NOT NULL,    -- LSB encoding: 1=HumeM 2=HumeF 3=ElvaanM 4=ElvaanF
                                        --               5=TaruM 6=TaruF 7=Mithra 8=Galka
  size            SMALLINT NOT NULL DEFAULT 1, -- 0=Small 1=Medium 2=Large
  face            SMALLINT NOT NULL DEFAULT 0, -- 0-15 (face 1-8 × A/B hair variant)
  main_job        SMALLINT NOT NULL DEFAULT 1, -- 1=WAR 2=MNK 3=WHM 4=BLM 5=RDM 6=THF
  nation          SMALLINT NOT NULL DEFAULT 0, -- 0=Sandy 1=Bastok 2=Windurst
  home_zone_id    INT NOT NULL,
  home_x          REAL NOT NULL,
  home_y          REAL NOT NULL,
  home_z          REAL NOT NULL,
  home_rot        REAL NOT NULL,
  current_zone_id INT NOT NULL,
  current_x       REAL NOT NULL,
  current_y       REAL NOT NULL,
  current_z       REAL NOT NULL,
  current_rot     REAL NOT NULL,
  deleted_at      TIMESTAMPTZ NULL,     -- NULL = active, set = soft-deleted
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_characters_name_active ON characters (LOWER(name)) WHERE deleted_at IS NULL;
CREATE INDEX idx_characters_account_active ON characters (account_id) WHERE deleted_at IS NULL;
```

### `character_jobs`

Per-character job level tracking, mirroring LSB `char_jobs`. Created alongside each character; initial values set based on the starting job chosen at creation.

```sql
CREATE TABLE character_jobs (
  character_id BIGINT PRIMARY KEY REFERENCES characters(id) ON DELETE CASCADE,
  war SMALLINT NOT NULL DEFAULT 0,
  mnk SMALLINT NOT NULL DEFAULT 0,
  whm SMALLINT NOT NULL DEFAULT 0,
  blm SMALLINT NOT NULL DEFAULT 0,
  rdm SMALLINT NOT NULL DEFAULT 0,
  thf SMALLINT NOT NULL DEFAULT 0,
  pld SMALLINT NOT NULL DEFAULT 0,
  drk SMALLINT NOT NULL DEFAULT 0,
  -- ... (all 22 FFXI jobs, advanced jobs default 0)
);
```

## Not Implemented in M1 (deferred)

| LSB Table | Reason deferred |
|---|---|
| `accounts_banned` | Ban system out of scope for M1 |
| `account_ip_record` | IP logging out of scope for M1 |
| `accounts_totp` | 2FA out of scope for M1 |

## Session Management Rules

- `PLAY` creates the `accounts_sessions` row. `CHAR_SELECT` only validates and loads identity; it does not create a session.
- A new `PLAY` while an `accounts_sessions` row exists for the account returns `ALREADY_ONLINE`.
- `LOGOUT` removes the row immediately.
- A scheduler runs every 10 seconds and hard-deletes rows where `last_seen_at` is older than 30 seconds.
- Auth tokens (pre-`PLAY`) are in-memory only and are not persisted to the database.

## Starting Zone Assignment

On character creation, the server randomly selects one of three zones within the chosen nation:

| Nation | Zone pool |
|---|---|
| 0 — San d'Oria | 230 (Southern San d'Oria), 231 (Northern San d'Oria), 232 (Port San d'Oria) |
| 1 — Bastok | 234 (Bastok Mines), 235 (Bastok Markets), 233 (Port Bastok) |
| 2 — Windurst | 238 (Windurst Waters), 239 (Port Windurst), 240 (Windurst Woods) |

Both `home_zone_id` and `current_zone_id` are initialised to the selected spawn zone at creation.

## Milestone 1 Done Criteria

- [x] Schema exists and is applied via server DDL on startup
- [x] Docker init SQL in `docker/postgres/init/001-schema.sql` matches runtime DDL
- [x] Uniqueness constraints prevent double-login at account and character level
- [x] Sessions are created on `PLAY`, updated on `PING`, and removed on `LOGOUT` or timeout
- [x] Character soft-delete sets `deleted_at`; soft-deleted characters are excluded from `CHAR_LIST`
