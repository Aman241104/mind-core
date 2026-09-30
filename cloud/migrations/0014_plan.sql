-- Key content from the "MS Abroad Plan" doc, brought in-app instead of linking out to a browser.
CREATE TABLE plan_actions (
  id TEXT PRIMARY KEY,
  when_text TEXT NOT NULL,
  action TEXT NOT NULL,
  why TEXT,
  order_hint INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE plan_universities (
  id TEXT PRIMARY KEY,
  rank INTEGER NOT NULL,
  name TEXT NOT NULL,
  country TEXT NOT NULL,
  tuition TEXT,
  scholarship TEXT,
  why_fits TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
