-- Voice notes added to an item: keep the recording so you can listen again, not just the transcript.
CREATE TABLE voice_notes (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  item_id    TEXT NOT NULL REFERENCES items(id) ON DELETE CASCADE,
  url        TEXT NOT NULL,
  transcript TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX voice_notes_item ON voice_notes(item_id);
