-- Graph view: when an item was last checked for "similar" neighbours in the meaning index.
ALTER TABLE items ADD COLUMN linked_at TEXT;
