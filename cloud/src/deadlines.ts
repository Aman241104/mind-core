// Deadlines and the calendar. Dates are Indian time (Asia/Kolkata); stored as YYYY-MM-DD.
import type { Env } from "./index.ts";

const MODEL = "@cf/openai/gpt-oss-120b";
const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });

export function todayIST(): string {
  return new Date(Date.now() + 330 * 60_000).toISOString().slice(0, 10);
}

// WhatsApp times are already local ("2026-03-23T21:30:52"); app saves are UTC ("...Z").
const SAVED_DAY = `CASE WHEN s.saved_at LIKE '%Z' THEN date(s.saved_at, '+330 minutes') ELSE date(s.saved_at) END`;

/** Ask the model for a deadline in some text. Returns YYYY-MM-DD or null; never guesses a year it can't infer. */
export async function findDeadline(env: Env, text: string, writtenOn: string = todayIST()): Promise<string | null> {
  if (!/\d|tomorrow|today|next|end of|deadline|last date|before|until|till|by /i.test(text)) return null;
  const out = (await env.AI.run(MODEL, {
    messages: [
      { role: "system", content: `This text was written on ${writtenOn} (India). Find a deadline in it: last date to apply, register, submit, buy at a price, or a date the person wants to be reminded. Reply with ONLY the date as YYYY-MM-DD, or NONE. If the year isn't said, use the first such date on or after ${writtenOn} (a past deadline is still a deadline). Ignore dates that are only history (founded, released).` },
      { role: "user", content: text.slice(0, 3000) },
    ],
    max_tokens: 400,
  } as never)) as { response?: string; choices?: { message: { content: string } }[] };
  const answer = (out.response ?? out.choices?.[0]?.message?.content ?? "").trim();
  const m = answer.match(/\d{4}-\d{2}-\d{2}/);
  if (!m || Number.isNaN(Date.parse(m[0]))) return null;
  // Models often skip a year ("before 15 Sep" written on 31 Aug → 2027). Take the first match on/after writtenOn.
  let d = m[0];
  while (Number(d.slice(0, 4)) > Number(writtenOn.slice(0, 4)) && `${Number(d.slice(0, 4)) - 1}${d.slice(4)}` >= writtenOn) {
    d = `${Number(d.slice(0, 4)) - 1}${d.slice(4)}`;
  }
  return d;
}

export async function setDeadline(env: Env, itemId: string, date: string | null, source: string): Promise<void> {
  await env.DB.prepare("UPDATE items SET deadline = ?, deadline_source = ?, updated_at = updated_at WHERE id = ?")
    .bind(date, date ? source : null, itemId).run();
}

/** PUT /v1/items/:id/deadline {date|null} — set or clear by hand. */
export async function putDeadline(id: string, req: Request, env: Env): Promise<Response> {
  const body = (await req.json()) as { date?: string | null };
  if (body.date && !/^\d{4}-\d{2}-\d{2}$/.test(body.date)) return json({ error: "date must be YYYY-MM-DD" }, 400);
  await setDeadline(env, id, body.date ?? null, "you");
  return json({ ok: true });
}

/** POST /v1/items/:id/find-deadline — the laptop researches it online (e.g. a job's last date to apply). */
export async function researchDeadline(id: string, env: Env): Promise<Response> {
  const it = await env.DB.prepare("SELECT name, kind, url FROM items WHERE id = ?").bind(id).first<{ name: string; kind: string; url: string | null }>();
  if (!it) return json({ error: "no such item" }, 404);
  const q = `Find the current deadline for: ${it.name} (${it.kind})${it.url ? `, ${it.url}` : ""}. ` +
    "For a job: last date to apply, or whether it is still open. For a course/cert: next enrolment or exam registration deadline. " +
    "For an offer: when it ends. Today is " + todayIST() + ".";
  const r = await env.DB.prepare("INSERT INTO research (question, item_id, kind) VALUES (?, ?, 'deadline') RETURNING id")
    .bind(q, id).first<{ id: number }>();
  return json({ id: r!.id });
}

/** GET /v1/calendar?month=YYYY-MM — what you saved each day, and deadlines. */
export async function calendar(url: URL, env: Env): Promise<Response> {
  const month = url.searchParams.get("month") ?? todayIST().slice(0, 7);
  if (!/^\d{4}-\d{2}$/.test(month)) return json({ error: "month must be YYYY-MM" }, 400);
  const [saved, deadlines] = await Promise.all([
    env.DB.prepare(
      `SELECT ${SAVED_DAY} AS day, i.id, i.name, i.kind, i.trust
       FROM saves s JOIN item_sources x ON x.save_id = s.id JOIN items i ON i.id = x.item_id
       WHERE s.shelf != 'work' AND substr(${SAVED_DAY}, 1, 7) = ?
       GROUP BY day, i.id ORDER BY day`,
    ).bind(month).all<{ day: string; id: string; name: string; kind: string; trust: string }>(),
    env.DB.prepare(
      `SELECT deadline AS day, id, name, kind, trust, deadline_source AS source FROM items
       WHERE deadline IS NOT NULL AND substr(deadline, 1, 7) = ? ORDER BY deadline`,
    ).bind(month).all(),
  ]);
  const days: Record<string, { id: string; name: string; kind: string; trust: string }[]> = {};
  for (const r of saved.results) (days[r.day] ??= []).push({ id: r.id, name: r.name, kind: r.kind, trust: r.trust });
  return json({ month, today: todayIST(), saved: days, deadlines: deadlines.results });
}

/** GET /v1/upcoming — the next deadlines, soonest first (and the last 3 days' overdue ones). */
export async function upcoming(env: Env): Promise<Response> {
  const rows = await env.DB.prepare(
    `SELECT id, name, kind, trust, status, deadline, deadline_source AS source FROM items
     WHERE deadline IS NOT NULL AND deadline >= date(?, '-3 days') AND status NOT IN ('done', 'skip')
     ORDER BY deadline LIMIT 20`,
  ).bind(todayIST()).all();
  return json({ today: todayIST(), items: rows.results });
}

/** One-off: look for deadlines in existing jobs/courses/certs (their claims + your notes). A few per call. */
export async function backfillDeadlines(env: Env): Promise<Response> {
  const rows = await env.DB.prepare(
    `SELECT i.id, i.name, i.user_note, group_concat(x.claims, ' ') AS claims,
       min(CASE WHEN s.saved_at LIKE '%Z' THEN date(s.saved_at, '+330 minutes') ELSE date(s.saved_at) END) AS saved
     FROM items i LEFT JOIN item_sources x ON x.item_id = i.id LEFT JOIN saves s ON s.id = x.save_id
     WHERE i.deadline IS NULL AND i.kind IN ('job', 'course', 'cert', 'other', 'tip') AND i.deadline_source IS NULL
     GROUP BY i.id LIMIT 8`,
  ).all<{ id: string; name: string; user_note: string | null; claims: string | null; saved: string | null }>();
  let found = 0;
  for (const r of rows.results) {
    const d = await findDeadline(env, `${r.name}. ${r.claims ?? ""} ${r.user_note ?? ""}`, r.saved ?? todayIST());
    // deadline_source 'checked' marks "looked, nothing found" so it isn't asked again.
    await env.DB.prepare("UPDATE items SET deadline = ?, deadline_source = ?, updated_at = updated_at WHERE id = ?")
      .bind(d, d ? "post" : "checked", r.id).run();
    if (d) found++;
  }
  return json({ checked: rows.results.length, found });
}
