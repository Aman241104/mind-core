ALTER TABLE plan_universities ADD COLUMN wishlisted INTEGER NOT NULL DEFAULT 0;

-- Real, sourced job-market notes per country — visa terms and outcomes, not fabricated stats.
CREATE TABLE plan_market (
  id TEXT PRIMARY KEY,
  country TEXT NOT NULL,
  post_study_visa TEXT NOT NULL,
  outlook TEXT NOT NULL,
  source_note TEXT,
  order_hint INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE plan_scholarships (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  place TEXT NOT NULL,
  amount TEXT,
  eligibility TEXT,
  deadline TEXT,
  order_hint INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
