// IELTS prep: curated resources, practice/mock tasks, and AI-graded attempts.
import type { Env } from "./index.ts";

const MODEL = "@cf/openai/gpt-oss-120b";
const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });
const bad = (message: string, status = 400) => json({ error: message }, status);

// Official IELTS band descriptors (ielts.org), condensed to what the model needs to place an answer.
const WRITING_RUBRIC = `IELTS Writing bands (Task Achievement/Response, Coherence & Cohesion, Lexical Resource, Grammatical Range & Accuracy):
Band 6: task addressed but unevenly; cohesive devices present but sometimes faulty or mechanical; adequate vocabulary with noticeable errors; mix of simple/complex sentences with frequent (non-impeding) errors.
Band 7: all parts of the task addressed, though some parts covered more fully than others; clear logical progression; sufficient vocabulary to discuss complex ideas with some flexibility, some errors in word choice/collocation; variety of complex structures, most sentences error-free.
Band 8: sufficiently addresses all parts, well-organised with skilful paragraphing; wide vocabulary used fluently and flexibly, only occasional inaccuracies; wide range of structures, majority error-free.
Band 9: fully and appropriately addresses all parts; cohesion attracts no attention; wide vocabulary with natural, sophisticated control; full flexibility and accuracy, rare minor slips only.
Only use a band below 6 if the response is genuinely weak.`;

const SPEAKING_RUBRIC = `IELTS Speaking bands (Fluency & Coherence, Lexical Resource, Grammatical Range & Accuracy, Pronunciation — graded here from a TRANSCRIPT, so pronunciation/intonation cannot be judged; say so explicitly in the "note" field and grade only the other three criteria):
Band 6: willing to speak at length but coherence sometimes lost through hesitation, repetition, or self-correction; adequate vocabulary despite inappropriacies; mix of simple/complex structures with limited flexibility.
Band 7: speaks at length without noticeable effort, occasional loss of coherence from hesitation; a range of connectives and discourse markers used with some flexibility; vocabulary for a variety of topics with some flexibility and paraphrase; complex structures with some flexibility, frequent error-free sentences.
Band 8: speaks fluently with only occasional repetition or self-correction, hesitation is content-related not word-searching; wide vocabulary flexibly used, skilful use of less common items; wide range of structures, majority error-free.
Band 9: speaks fluently with only rare repetition/self-correction; full flexibility and precise vocabulary usage; full flexibility and accuracy across structures.`;

// Raw-score -> band conversion (ielts.org / ielts.com.au published tables; typical, not test-specific).
const LISTENING_BANDS: [number, number][] = [
  [39, 9], [37, 8.5], [35, 8], [32, 7.5], [30, 7], [26, 6.5], [23, 6], [18, 5.5], [16, 5],
];
const READING_BANDS: [number, number][] = [
  [39, 9], [37, 8.5], [35, 8], [33, 7.5], [30, 7], [27, 6.5], [23, 6], [19, 5.5], [15, 5],
];
function rawToBand(raw: number, table: [number, number][]): number {
  for (const [min, band] of table) if (raw >= min) return band;
  return 4;
}

export async function listResources(url: URL, env: Env): Promise<Response> {
  const skill = url.searchParams.get("skill");
  const rows = skill
    ? await env.DB.prepare("SELECT * FROM ielts_resources WHERE skill = ? ORDER BY order_hint, created_at").bind(skill).all()
    : await env.DB.prepare("SELECT * FROM ielts_resources ORDER BY skill, order_hint, created_at").all();
  return json({ resources: rows.results });
}

export async function listTasks(url: URL, env: Env): Promise<Response> {
  const skill = url.searchParams.get("skill");
  const taskType = url.searchParams.get("task_type");
  let q = "SELECT id, skill, task_type, title, prompt, difficulty, created_at FROM ielts_tasks WHERE 1=1";
  const binds: string[] = [];
  if (skill) { q += " AND skill = ?"; binds.push(skill); }
  if (taskType) { q += " AND task_type = ?"; binds.push(taskType); }
  q += " ORDER BY created_at";
  const rows = await env.DB.prepare(q).bind(...binds).all();
  return json({ tasks: rows.results });
}

export async function getTask(id: string, env: Env): Promise<Response> {
  const row = await env.DB.prepare("SELECT * FROM ielts_tasks WHERE id = ?").bind(id).first();
  if (!row) return bad("no such task", 404);
  return json({ task: row });
}

type AttemptBody = {
  task_id?: string;
  mode: "diagnostic" | "mock" | "practice";
  skill: "writing" | "speaking" | "reading" | "listening";
  response: string; // essay/transcript text, or a JSON array of answers for reading/listening
};

export async function submitAttempt(req: Request, env: Env): Promise<Response> {
  const body = (await req.json()) as AttemptBody;
  if (!body?.response || !body.skill || !body.mode) return bad("mode, skill, response required");
  const id = crypto.randomUUID();

  if (body.skill === "writing" || body.skill === "speaking") {
    const task = body.task_id
      ? await env.DB.prepare("SELECT prompt, task_type FROM ielts_tasks WHERE id = ?").bind(body.task_id).first<{ prompt: string; task_type: string }>()
      : null;
    const rubric = body.skill === "writing" ? WRITING_RUBRIC : SPEAKING_RUBRIC;
    const kindLabel = body.skill === "writing" ? "essay" : "spoken-answer transcript";
    const out = (await env.AI.run(MODEL, {
      messages: [
        {
          role: "system",
          content: `You are an IELTS examiner. Grade the candidate's ${kindLabel} strictly against these official band descriptors:\n${rubric}\nReply with ONLY a JSON object: {"band": <number, one of 5,5.5,6,6.5,7,7.5,8,8.5,9>, "strengths": ["..."], "fixes": ["specific, actionable fixes, each naming which criterion it targets"], "note": "one short line"}`,
        },
        { role: "user", content: `${task ? `Task (${task.task_type}): ${task.prompt}\n\n` : ""}Candidate's ${kindLabel}:\n${body.response.slice(0, 6000)}` },
      ],
      max_tokens: 2000, reasoning: { effort: "low" },
    } as never)) as { response?: string; choices?: { message: { content: string } }[] };
    const raw = (out.response ?? out.choices?.[0]?.message?.content ?? "").trim();
    let parsed: { band?: number; strengths?: string[]; fixes?: string[]; note?: string } = {};
    const start = raw.indexOf("{");
    const end = raw.lastIndexOf("}");
    try {
      parsed = JSON.parse(start >= 0 && end > start ? raw.slice(start, end + 1) : raw);
    } catch {
      parsed = { fixes: [raw || "The model returned no gradable output — try resubmitting."] };
    }
    await env.DB.prepare(
      "INSERT INTO ielts_attempts (id, task_id, mode, skill, response, band, feedback) VALUES (?, ?, ?, ?, ?, ?, ?)",
    ).bind(id, body.task_id ?? null, body.mode, body.skill, body.response, parsed.band ?? null, JSON.stringify(parsed)).run();
    return json({ id, band: parsed.band ?? null, feedback: parsed });
  }

  // Reading / Listening: objective, scored against the task's stored answer key.
  if (!body.task_id) return bad("task_id required for reading/listening");
  const task = await env.DB.prepare("SELECT content_json FROM ielts_tasks WHERE id = ?").bind(body.task_id).first<{ content_json: string | null }>();
  if (!task?.content_json) return bad("task has no answer key", 404);
  const key = JSON.parse(task.content_json) as { answers: string[] };
  const given = JSON.parse(body.response) as string[];
  const correct = key.answers.reduce((n, a, i) => n + ((given[i] ?? "").trim().toLowerCase() === a.trim().toLowerCase() ? 1 : 0), 0);
  // The raw-score->band table is normed on a full ~40-question section; a short passage can only report accuracy.
  const fullSection = key.answers.length >= 35;
  const band = fullSection ? rawToBand(correct, body.skill === "listening" ? LISTENING_BANDS : READING_BANDS) : null;
  await env.DB.prepare(
    "INSERT INTO ielts_attempts (id, task_id, mode, skill, response, score_raw, score_total, band) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
  ).bind(id, body.task_id, body.mode, body.skill, body.response, correct, key.answers.length, band).run();
  return json({
    id, score_raw: correct, score_total: key.answers.length, band,
    note: fullSection ? undefined : "Band estimates need a full ~40-question section — this short passage only gives accuracy.",
  });
}

export async function listAttempts(url: URL, env: Env): Promise<Response> {
  const skill = url.searchParams.get("skill");
  const rows = skill
    ? await env.DB.prepare(
        "SELECT id, task_id, mode, skill, score_raw, score_total, band, feedback, created_at FROM ielts_attempts WHERE skill = ? ORDER BY created_at DESC LIMIT 50",
      ).bind(skill).all()
    : await env.DB.prepare(
        "SELECT id, task_id, mode, skill, score_raw, score_total, band, feedback, created_at FROM ielts_attempts ORDER BY created_at DESC LIMIT 50",
      ).all();
  return json({ attempts: rows.results });
}

/** GET /v1/ielts/progress — latest band per skill + a simple overall estimate. */
export async function progress(env: Env): Promise<Response> {
  const latest = await env.DB.prepare(
    `SELECT skill, band, created_at FROM ielts_attempts a
     WHERE band IS NOT NULL AND created_at = (SELECT MAX(created_at) FROM ielts_attempts b WHERE b.skill = a.skill AND b.band IS NOT NULL)`,
  ).all<{ skill: string; band: number; created_at: string }>();
  const overall = latest.results.length
    ? Math.round((latest.results.reduce((s, r) => s + r.band, 0) / latest.results.length) * 2) / 2
    : null;
  return json({ by_skill: latest.results, overall_estimate: overall });
}

/** POST /v1/brain/ielts/seed — one-off bulk load of resources/tasks (run from the laptop, not the phone). */
export async function seedContent(req: Request, env: Env): Promise<Response> {
  const body = (await req.json()) as { resources?: Record<string, unknown>[]; tasks?: Record<string, unknown>[] };
  let addedResources = 0, addedTasks = 0;
  for (const r of body.resources ?? []) {
    await env.DB.prepare(
      "INSERT INTO ielts_resources (id, skill, kind, title, url, note, band_focus, order_hint) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
    ).bind(crypto.randomUUID(), r.skill, r.kind, r.title, r.url ?? null, r.note ?? null, r.band_focus ?? null, r.order_hint ?? 0).run();
    addedResources++;
  }
  for (const t of body.tasks ?? []) {
    await env.DB.prepare(
      "INSERT INTO ielts_tasks (id, skill, task_type, title, prompt, content_json, difficulty) VALUES (?, ?, ?, ?, ?, ?, ?)",
    ).bind(
      crypto.randomUUID(), t.skill, t.task_type, t.title, t.prompt,
      t.content_json ? JSON.stringify(t.content_json) : null, t.difficulty ?? "target",
    ).run();
    addedTasks++;
  }
  return json({ addedResources, addedTasks });
}
