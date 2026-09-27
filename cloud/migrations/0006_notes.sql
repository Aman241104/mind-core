-- P2: your own notes and ideas. An idea is a short note with a stage.
CREATE TABLE notes (
  id          TEXT PRIMARY KEY,
  kind        TEXT NOT NULL DEFAULT 'note',   -- note | idea
  title       TEXT NOT NULL DEFAULT '',
  body        TEXT NOT NULL DEFAULT '',       -- markdown: headings, "- [ ]" checklists, bullets, [[links]]
  stage       TEXT,                           -- ideas: spark | growing | ready | done | parked
  color       INTEGER NOT NULL DEFAULT 0,     -- index into the app's pastel palette
  pinned      INTEGER NOT NULL DEFAULT 0,
  favorite    INTEGER NOT NULL DEFAULT 0,
  voice_url   TEXT,
  source      TEXT NOT NULL DEFAULT 'app',    -- app | voice | drawer | widget
  created_at  TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at  TEXT NOT NULL DEFAULT (datetime('now')),
  deleted_at  TEXT                            -- in the trash (kept 30 days)
);
CREATE INDEX notes_list ON notes(deleted_at, kind, updated_at);

-- Links out of a note: [[mentions]] you wrote, and related things found by meaning.
CREATE TABLE note_links (
  src_id   TEXT NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
  dst_id   TEXT NOT NULL,
  dst_type TEXT NOT NULL,                     -- note | item
  type     TEXT NOT NULL,                     -- mention | related
  score    REAL,
  PRIMARY KEY (src_id, dst_id, type)
);
CREATE INDEX note_links_dst ON note_links(dst_id);

CREATE VIRTUAL TABLE notes_fts USING fts5(note_id UNINDEXED, title, body, tokenize = 'porter unicode61');
