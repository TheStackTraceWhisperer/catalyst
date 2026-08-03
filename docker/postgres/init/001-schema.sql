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
  zone_id INT NOT NULL DEFAULT 0,
  last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (account_id),
  UNIQUE (character_id)
);

CREATE INDEX IF NOT EXISTS idx_accounts_sessions_last_seen
  ON accounts_sessions(last_seen_at);

-- LSB race encoding: 1=HumeM 2=HumeF 3=ElvaanM 4=ElvaanF 5=TaruM 6=TaruF 7=Mithra 8=Galka
-- size: 0=Small 1=Medium 2=Large (Tarutaru=0, Galka=2, others 0-2)
-- face: 0-15 (face 1-8 x A/B hair variant)
-- main_job: 1=WAR 2=MNK 3=WHM 4=BLM 5=RDM 6=THF
-- nation: 0=Sandy 1=Bastok 2=Windurst (random zone chosen at creation)
CREATE TABLE IF NOT EXISTS characters (
  id BIGSERIAL PRIMARY KEY,
  account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  name VARCHAR(16) NOT NULL,
  race SMALLINT NOT NULL,
  size SMALLINT NOT NULL DEFAULT 1,
  face SMALLINT NOT NULL DEFAULT 0,
  main_job SMALLINT NOT NULL DEFAULT 1,
  nation SMALLINT NOT NULL DEFAULT 0,
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

-- Mirrors LSB char_jobs: per-job level storage (0 = locked, 1+ = level)
CREATE TABLE IF NOT EXISTS character_jobs (
  character_id BIGINT PRIMARY KEY REFERENCES characters(id) ON DELETE CASCADE,
  war SMALLINT NOT NULL DEFAULT 0,
  mnk SMALLINT NOT NULL DEFAULT 0,
  whm SMALLINT NOT NULL DEFAULT 0,
  blm SMALLINT NOT NULL DEFAULT 0,
  rdm SMALLINT NOT NULL DEFAULT 0,
  thf SMALLINT NOT NULL DEFAULT 0,
  pld SMALLINT NOT NULL DEFAULT 0,
  drk SMALLINT NOT NULL DEFAULT 0,
  bst SMALLINT NOT NULL DEFAULT 0,
  brd SMALLINT NOT NULL DEFAULT 0,
  rng SMALLINT NOT NULL DEFAULT 0,
  sam SMALLINT NOT NULL DEFAULT 0,
  nin SMALLINT NOT NULL DEFAULT 0,
  drg SMALLINT NOT NULL DEFAULT 0,
  smn SMALLINT NOT NULL DEFAULT 0,
  blu SMALLINT NOT NULL DEFAULT 0,
  cor SMALLINT NOT NULL DEFAULT 0,
  pup SMALLINT NOT NULL DEFAULT 0,
  dnc SMALLINT NOT NULL DEFAULT 0,
  sch SMALLINT NOT NULL DEFAULT 0,
  geo SMALLINT NOT NULL DEFAULT 0,
  run SMALLINT NOT NULL DEFAULT 0
);
