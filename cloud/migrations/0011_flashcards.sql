-- Flashcards made from a note or a saved item, reviewed with SM-2 spaced repetition.
CREATE TABLE flashcards (
  id          TEXT PRIMARY KEY,
  source_type TEXT NOT NULL,              -- note | item
  source_id   TEXT NOT NULL,
  q           TEXT NOT NULL,
  a           TEXT NOT NULL,
  due         TEXT NOT NULL DEFAULT (date('now')),
  interval    INTEGER NOT NULL DEFAULT 0, -- days
  ease        REAL NOT NULL DEFAULT 2.5,
  reps        INTEGER NOT NULL DEFAULT 0,
  created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX flashcards_due ON flashcards(due);
CREATE INDEX flashcards_source ON flashcards(source_type, source_id);
