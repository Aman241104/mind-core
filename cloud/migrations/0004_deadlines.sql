-- Deadlines on items (apply-by, registration closes, offer ends...), and research tied to an item.
ALTER TABLE items ADD COLUMN deadline TEXT;          -- YYYY-MM-DD
ALTER TABLE items ADD COLUMN deadline_source TEXT;   -- post | note | voice | research | you
CREATE INDEX items_deadline ON items(deadline);
ALTER TABLE research ADD COLUMN item_id TEXT;
ALTER TABLE research ADD COLUMN kind TEXT NOT NULL DEFAULT 'question'; -- question | deadline
