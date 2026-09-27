-- M3: text read off screens/pages is kept (it's searchable), and research questions for the laptop.
ALTER TABLE saves ADD COLUMN screen_text TEXT;
ALTER TABLE saves ADD COLUMN indexed INTEGER NOT NULL DEFAULT 0; -- 1 once its chunks are in mindcore-chunks

CREATE TABLE research (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  question    TEXT NOT NULL,
  engine      TEXT NOT NULL DEFAULT 'claude',     -- claude (laptop)
  status      TEXT NOT NULL DEFAULT 'pending',    -- pending | leased | done | failed
  answer      TEXT,
  sources     TEXT,                               -- JSON [{title, url}]
  error       TEXT,
  lease_until TEXT,
  created_at  TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX research_pick ON research(status, id);
