-- IELTS prep: curated resources, practice/mock content, and graded attempts.
CREATE TABLE ielts_resources (
  id          TEXT PRIMARY KEY,
  skill       TEXT NOT NULL,               -- listening | reading | writing | speaking | general
  kind        TEXT NOT NULL,                -- technique | free_test | video | article | template
  title       TEXT NOT NULL,
  url         TEXT,
  note        TEXT,                         -- why it matters / what it covers
  band_focus  TEXT,                         -- e.g. "6->7", "7->8"
  order_hint  INTEGER NOT NULL DEFAULT 0,
  created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX ielts_resources_skill ON ielts_resources(skill, order_hint);

CREATE TABLE ielts_tasks (
  id           TEXT PRIMARY KEY,
  skill        TEXT NOT NULL,               -- writing | speaking | reading | listening
  task_type    TEXT NOT NULL,                -- task1 | task2 | part1 | part2 | part3 | passage | section
  title        TEXT NOT NULL,
  prompt       TEXT NOT NULL,                -- the question/passage/cue card text
  content_json TEXT,                         -- structured extras: chart data, MCQ options+answers, audio script
  difficulty   TEXT NOT NULL DEFAULT 'target', -- easier | target | stretch
  created_at   TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX ielts_tasks_skill ON ielts_tasks(skill, task_type);

CREATE TABLE ielts_attempts (
  id           TEXT PRIMARY KEY,
  task_id      TEXT REFERENCES ielts_tasks(id),
  mode         TEXT NOT NULL,               -- diagnostic | mock | practice
  skill        TEXT NOT NULL,
  response     TEXT NOT NULL,               -- essay text, speaking transcript, or MCQ answers JSON
  score_raw    INTEGER,                     -- correct count, objective sections only
  score_total  INTEGER,
  band         REAL,                        -- estimated band for this attempt
  feedback     TEXT,                         -- LLM feedback JSON: strengths, fixes, descriptor citations
  created_at   TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX ielts_attempts_skill ON ielts_attempts(skill, created_at);
