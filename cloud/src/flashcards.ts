// P4: flashcards. Swan writes question/answer cards from a note or a saved item (only from what's in it), and
// reviews follow SM-2: "again" resets, "hard" / "good" / "easy" stretch the gap by the card's ease.
import type { Env } from "./index.ts";
import { todayIST } from "./deadlines.ts";

const WRITER = "@cf/openai/gpt-oss-120b";

const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });

function newId(): string {
  return [...crypto.getRandomValues(new Uint8Array(8))].map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function sourceText(env: Env, type: string, id: string): Promise<{ title: string; text: string } | null> {
  if (type === "note") {
    const n = await env.DB.prepare("SELECT title, body FROM notes WHERE id = ? AND deleted_at IS NULL").bind(id).first<{ title: string; body: string }>();
    return n ? { title: n.title || "Untitled", text: `${n.title}\n\n${n.body}` } : null;
  }
  const it = await env.DB.prepare("SELECT name, kind, one_line, user_note, verification FROM items WHERE id = ?").bind(id)
    .first<{ name: string; kind: string; one_line: string | null; user_note: string | null; verification: string | null }>();
  if (!it) return null;
  // What the saved posts said, from the indexed text (the same text Ask quotes).
  const saves = await env.DB.prepare(
    `SELECT s.caption, s.screen_text, s.transcript FROM item_sources x JOIN saves s ON s.id = x.save_id WHERE x.item_id = ? LIMIT 3`,
  ).bind(id).all<{ caption: string | null; screen_text: string | null; transcript: string | null }>().catch(() => ({ results: [] }));
  const said = saves.results.map((s) => [s.caption, s.transcript, s.screen_text].filter(Boolean).join(" ")).join("\n").slice(0, 5000);
  return { title: it.name, text: `${it.name} (${it.kind}). ${it.one_line ?? ""}\n${it.user_note ?? ""}\n${said}` };
}

/** POST /v1/flashcards {type, id} — make 3 to 8 cards from one note or item (replaces earlier cards from it). */
export async function makeCards(req: Request, env: Env): Promise<Response> {
  const b = (await req.json()) as { type?: string; id?: string };
  if ((b.type !== "note" && b.type !== "item") || !b.id) return json({ error: "need type (note|item) and id" }, 400);
  const src = await sourceText(env, b.type, b.id);
  if (!src || src.text.trim().length < 40) return json({ error: "not enough in there to make cards from" }, 400);
  const out = (await env.AI.run(WRITER, {
    messages: [
      { role: "system", content: `Write flashcards for remembering the useful facts in this text: what it is, what it's for, key names, numbers, commands, steps, dates. 3 to 8 cards.
Questions short and specific (never yes/no); answers one or two short lines. Only facts that are in the text, each card about a different fact. Skip the creator's calls to like, comment, follow, DM or "link in bio", and anything about the video itself. No em dashes.
Reply with JSON only: {"cards":[{"q":"...","a":"..."}]}` },
      { role: "user", content: src.text.slice(0, 7000) },
    ],
    max_tokens: 2500, reasoning: { effort: "low" }, response_format: { type: "json_object" },
  } as never)) as { response?: string | object; choices?: { message: { content: string } }[] };
  const raw = out.response ?? out.choices?.[0]?.message?.content ?? "";
  let cards: { q: string; a: string }[] = [];
  try {
    const parsed = (typeof raw === "string" ? JSON.parse(raw.slice(raw.indexOf("{"), raw.lastIndexOf("}") + 1)) : raw) as { cards?: unknown };
    cards = (Array.isArray(parsed.cards) ? parsed.cards : []).map((c) => c as { q?: unknown; a?: unknown })
      .filter((c) => typeof c.q === "string" && typeof c.a === "string" && c.q.trim() && c.a.trim())
      .map((c) => ({ q: String(c.q).trim().slice(0, 300), a: String(c.a).trim().replace(/—/g, ",").slice(0, 600) }));
  } catch {
    const text = typeof raw === "string" ? raw : "";
    for (const m of text.matchAll(/"q"\s*:\s*"((?:[^"\\]|\\.)+)"\s*,\s*"a"\s*:\s*"((?:[^"\\]|\\.)+)"/g)) cards.push({ q: m[1], a: m[2] });
  }
  cards = cards.slice(0, 8);
  if (!cards.length) return json({ error: "Swan couldn't find facts worth a card, try again" }, 502);
  await env.DB.batch([
    env.DB.prepare("DELETE FROM flashcards WHERE source_type = ? AND source_id = ?").bind(b.type, b.id),
    ...cards.map((c) => env.DB.prepare("INSERT INTO flashcards (id, source_type, source_id, q, a, due) VALUES (?, ?, ?, ?, ?, ?)")
      .bind(newId(), b.type, b.id, c.q, c.a, todayIST())),
  ]);
  return json({ made: cards.length, title: src.title, cards });
}

/** GET /v1/flashcards/due — cards due today (with where they came from), and how many exist in all. */
export async function dueCards(env: Env): Promise<Response> {
  const today = todayIST();
  const [due, total] = await Promise.all([
    env.DB.prepare(
      `SELECT f.id, f.q, f.a, f.source_type, f.source_id, f.reps, COALESCE(n.title, i.name) AS source_title
       FROM flashcards f LEFT JOIN notes n ON f.source_type = 'note' AND n.id = f.source_id
       LEFT JOIN items i ON f.source_type = 'item' AND i.id = f.source_id
       WHERE f.due <= ? AND (f.source_type = 'item' OR n.deleted_at IS NULL) ORDER BY f.due, f.reps LIMIT 40`,
    ).bind(today).all(),
    env.DB.prepare("SELECT COUNT(*) AS n FROM flashcards").first<{ n: number }>(),
  ]);
  return json({ today, due: due.results, total: total?.n ?? 0 });
}

/** POST /v1/flashcards/:id/review {grade: again|hard|good|easy} — SM-2 scheduling. */
export async function reviewCard(id: string, req: Request, env: Env): Promise<Response> {
  const { grade } = (await req.json()) as { grade?: string };
  const q = { again: 1, hard: 3, good: 4, easy: 5 }[grade ?? ""];
  if (!q) return json({ error: "grade must be again, hard, good or easy" }, 400);
  const c = await env.DB.prepare("SELECT interval, ease, reps FROM flashcards WHERE id = ?").bind(id)
    .first<{ interval: number; ease: number; reps: number }>();
  if (!c) return json({ error: "no such card" }, 404);
  let { interval, ease, reps } = c;
  if (q < 3) { reps = 0; interval = 1; }
  else {
    reps += 1;
    interval = reps === 1 ? 1 : reps === 2 ? (q === 5 ? 4 : 3) : Math.round(interval * ease * (q === 3 ? 0.8 : q === 5 ? 1.3 : 1));
  }
  ease = Math.max(1.3, ease + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)));
  const due = new Date(Date.parse(todayIST()) + interval * 86_400_000).toISOString().slice(0, 10);
  await env.DB.prepare("UPDATE flashcards SET interval = ?, ease = ?, reps = ?, due = ? WHERE id = ?").bind(interval, ease, reps, due, id).run();
  return json({ due, interval });
}
