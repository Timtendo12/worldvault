-- One row per install. Timestamps are UTC Unix milliseconds.
CREATE TABLE IF NOT EXISTS installs (
  id         TEXT PRIMARY KEY,
  first_seen INTEGER NOT NULL,
  last_seen  INTEGER NOT NULL,
  worlds     INTEGER NOT NULL DEFAULT 0 CHECK (worlds >= 0 AND worlds <= 1000000)
);

CREATE INDEX IF NOT EXISTS idx_installs_last_seen ON installs(last_seen);
