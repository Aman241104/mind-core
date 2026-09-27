-- Brainstorm boards: a canvas of cards (sticky text, or a note / saved item placed on it) and lines between them.
CREATE TABLE boards (
  id          TEXT PRIMARY KEY,
  title       TEXT NOT NULL DEFAULT '',
  created_at  TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at  TEXT NOT NULL DEFAULT (datetime('now')),
  deleted_at  TEXT
);
CREATE TABLE board_cards (
  board_id TEXT NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
  id       TEXT NOT NULL,              -- client-made, unique within the board
  type     TEXT NOT NULL,              -- text | note | item
  ref_id   TEXT,                       -- note or item id
  text     TEXT NOT NULL DEFAULT '',   -- sticky text
  x        REAL NOT NULL DEFAULT 0,
  y        REAL NOT NULL DEFAULT 0,
  color    INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (board_id, id)
);
CREATE TABLE board_edges (
  board_id TEXT NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
  a        TEXT NOT NULL,
  b        TEXT NOT NULL,
  PRIMARY KEY (board_id, a, b)
);
