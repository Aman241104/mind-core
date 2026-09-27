-- Dates in to-do lines ("- [ ] send the form by Oct 3"), found once per line text and cached.
CREATE TABLE note_dues (
  note_id TEXT NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
  text    TEXT NOT NULL,
  due     TEXT,              -- YYYY-MM-DD, or NULL = looked, no date
  PRIMARY KEY (note_id, text)
);
CREATE INDEX note_dues_due ON note_dues(due);
