-- mind-core schema (D1 / SQLite)

-- One row per thing you saved: a link, a screenshot, a note. id = sha1(normalized url)[:16] for links.
CREATE TABLE saves (
  id          TEXT PRIMARY KEY,
  url         TEXT,
  raw_url     TEXT,
  host        TEXT,
  kind_hint   TEXT NOT NULL,              -- reel | post | github | chat_share | page | image | text
  shelf       TEXT NOT NULL,              -- learning | work | unsure
  mine        INTEGER NOT NULL DEFAULT 0, -- your own repo / deploy
  title       TEXT,
  note        TEXT,
  source      TEXT NOT NULL,              -- whatsapp | share | paste | import
  saved_at    TEXT NOT NULL,              -- when you saved it (e.g. WhatsApp message time)
  created_at  TEXT NOT NULL DEFAULT (datetime('now')),
  status      TEXT NOT NULL DEFAULT 'queued', -- queued | processing | done | failed | skipped
  error       TEXT,
  creator     TEXT,
  caption     TEXT,
  transcript  TEXT,
  language    TEXT,
  promo       INTEGER
);
CREATE INDEX saves_status ON saves(status);
CREATE INDEX saves_shelf ON saves(shelf, saved_at);

-- Things worth keeping, pulled out of saves. The same repo from 3 reels is ONE item.
CREATE TABLE items (
  id            TEXT PRIMARY KEY,           -- sha1(canonical_key)[:16]
  canonical_key TEXT NOT NULL UNIQUE,       -- github:owner/repo | name:<kind>:<lowercased name>
  kind          TEXT NOT NULL,              -- repo | tool | cert | course | job | tip | other
  name          TEXT NOT NULL,
  url           TEXT,
  one_line      TEXT,
  shelf         TEXT NOT NULL DEFAULT 'learning',
  trust         TEXT NOT NULL DEFAULT 'unconfirmed', -- verified | check | unconfirmed | dead | hype
  verification  TEXT,                       -- JSON: stars, license, pushed_at, archived, checked_at, notes
  status        TEXT NOT NULL DEFAULT 'new', -- new | want | trying | done | skip
  user_note     TEXT,
  created_at    TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at    TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX items_kind ON items(kind, updated_at);

-- Which saves mentioned an item, and what each one claimed about it.
CREATE TABLE item_sources (
  item_id TEXT NOT NULL REFERENCES items(id) ON DELETE CASCADE,
  save_id TEXT NOT NULL REFERENCES saves(id) ON DELETE CASCADE,
  claims  TEXT,                              -- JSON array of strings
  needs_frames INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (item_id, save_id)
);

-- Links between items: mentioned_together | alternative_to | needs | same_topic
CREATE TABLE relations (
  a    TEXT NOT NULL REFERENCES items(id) ON DELETE CASCADE,
  b    TEXT NOT NULL REFERENCES items(id) ON DELETE CASCADE,
  type TEXT NOT NULL,
  PRIMARY KEY (a, b, type)
);

-- Work for the laptop brain (or the cloud cron). Leased so a crashed worker's jobs come back.
CREATE TABLE jobs (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  save_id     TEXT NOT NULL REFERENCES saves(id) ON DELETE CASCADE,
  type        TEXT NOT NULL,                -- reel | page | github | chat_share | image
  runner      TEXT NOT NULL DEFAULT 'brain', -- brain | cloud
  status      TEXT NOT NULL DEFAULT 'pending', -- pending | leased | done | failed
  attempts    INTEGER NOT NULL DEFAULT 0,
  lease_until TEXT,
  error       TEXT,
  created_at  TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX jobs_pick ON jobs(runner, status, id);

-- Laptop heartbeat, for the "Laptop online" dot.
CREATE TABLE brain (
  id        TEXT PRIMARY KEY,
  last_seen TEXT NOT NULL,
  info      TEXT
);

-- Keyword search (the "exact names" half of hybrid search).
CREATE VIRTUAL TABLE items_fts USING fts5(item_id UNINDEXED, name, one_line, claims, tokenize = 'porter unicode61');
