CREATE TABLE IF NOT EXISTS accounts (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS accounts_sessions (
  session_id UUID PRIMARY KEY,
  account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  character_id BIGINT NOT NULL,
  zone_id INT NOT NULL,
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (account_id),
  UNIQUE (character_id)
);

CREATE INDEX IF NOT EXISTS idx_accounts_sessions_last_seen
  ON accounts_sessions(last_seen_at);

CREATE TABLE IF NOT EXISTS characters (
  id BIGSERIAL PRIMARY KEY,
  account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  name VARCHAR(16) NOT NULL,
  race SMALLINT NOT NULL,
  gender CHAR(1) NOT NULL DEFAULT 'M',
  face SMALLINT NOT NULL,
  starting_city VARCHAR(16) NOT NULL,
  home_zone_id INT NOT NULL,
  home_x REAL NOT NULL,
  home_y REAL NOT NULL,
  home_z REAL NOT NULL,
  home_rot REAL NOT NULL,
  current_zone_id INT NOT NULL,
  current_x REAL NOT NULL,
  current_y REAL NOT NULL,
  current_z REAL NOT NULL,
  current_rot REAL NOT NULL,
  deleted_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_characters_name_active
  ON characters (LOWER(name))
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_characters_account_active
  ON characters (account_id)
  WHERE deleted_at IS NULL;
