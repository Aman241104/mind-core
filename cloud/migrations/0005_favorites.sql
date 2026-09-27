-- Favorites: a star on anything you want to keep close.
ALTER TABLE items ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0;
