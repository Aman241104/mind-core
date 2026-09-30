-- University/scholarship news for the MS-abroad shortlist (Ireland/Europe/NZ), fed by watched pages.
CREATE TABLE uni_news (
  id           TEXT PRIMARY KEY,           -- sha1(source_url + headline)
  university   TEXT NOT NULL,
  country      TEXT NOT NULL,
  source_url   TEXT NOT NULL,
  headline     TEXT NOT NULL,
  summary      TEXT,
  kind         TEXT NOT NULL DEFAULT 'update', -- deadline | scholarship | fee_change | intake | update
  detected_at  TEXT NOT NULL DEFAULT (datetime('now')),
  seen         INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX uni_news_detected ON uni_news(detected_at DESC);
CREATE INDEX uni_news_seen ON uni_news(seen, detected_at DESC);
