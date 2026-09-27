// P3: brainstorm boards. The app owns the layout and saves the whole board at once (PUT); cards that point at a
// note or item get their current title on read, so renames show up everywhere.
import type { Env } from "./index.ts";

const WRITER = "@cf/openai/gpt-oss-120b";
const MAX_CARDS = 300;

const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });

function newId(): string {
  return [...crypto.getRandomValues(new Uint8Array(8))].map((b) => b.toString(16).padStart(2, "0")).join("");
}

interface CardIn { id: string; type: string; ref_id?: string | null; text?: string; x: number; y: number; color?: number }

export async function listBoards(env: Env): Promise<Response> {
  const rows = await env.DB.prepare(
    `SELECT b.id, b.title, b.updated_at, (SELECT COUNT(*) FROM board_cards c WHERE c.board_id = b.id) AS cards
     FROM boards b WHERE deleted_at IS NULL ORDER BY updated_at DESC LIMIT 200`,
  ).all();
  return json(rows.results);
}

export async function getBoard(id: string, env: Env): Promise<Response> {
  const b = await env.DB.prepare("SELECT id, title, updated_at FROM boards WHERE id = ? AND deleted_at IS NULL").bind(id).first();
  if (!b) return json({ error: "no such board" }, 404);
  const [cards, edges] = await Promise.all([
    env.DB.prepare(
      `SELECT c.id, c.type, c.ref_id, c.text, c.x, c.y, c.color,
         COALESCE(n.title, i.name) AS title, COALESCE(n.kind, i.kind) AS kind, i.one_line AS subtitle
       FROM board_cards c
       LEFT JOIN notes n ON c.type = 'note' AND n.id = c.ref_id
       LEFT JOIN items i ON c.type = 'item' AND i.id = c.ref_id
       WHERE c.board_id = ?`,
    ).bind(id).all(),
    env.DB.prepare("SELECT a, b FROM board_edges WHERE board_id = ?").bind(id).all(),
  ]);
  return json({ ...b, cards: cards.results, edges: edges.results });
}

export async function createBoard(req: Request, env: Env): Promise<Response> {
  const body = (await req.json()) as { title?: string };
  const id = newId();
  await env.DB.prepare("INSERT INTO boards (id, title) VALUES (?, ?)").bind(id, (body.title ?? "").slice(0, 200)).run();
  return getBoard(id, env);
}

/** Replace the board's title, cards and lines with what the app has. */
export async function saveBoard(id: string, req: Request, env: Env): Promise<Response> {
  const body = (await req.json()) as { title?: string; cards?: CardIn[]; edges?: { a: string; b: string }[] };
  const ok = await env.DB.prepare("SELECT 1 FROM boards WHERE id = ? AND deleted_at IS NULL").bind(id).first();
  if (!ok) return json({ error: "no such board" }, 404);
  const cards = (body.cards ?? []).slice(0, MAX_CARDS).filter((c) => c.id && ["text", "note", "item"].includes(c.type));
  const ids = new Set(cards.map((c) => c.id));
  const edges = (body.edges ?? []).filter((e) => ids.has(e.a) && ids.has(e.b) && e.a !== e.b).slice(0, 600);
  const stmts: D1PreparedStatement[] = [
    env.DB.prepare("UPDATE boards SET title = COALESCE(?, title), updated_at = datetime('now') WHERE id = ?").bind(body.title?.slice(0, 200) ?? null, id),
    env.DB.prepare("DELETE FROM board_cards WHERE board_id = ?").bind(id),
    env.DB.prepare("DELETE FROM board_edges WHERE board_id = ?").bind(id),
    ...cards.map((c) => env.DB.prepare("INSERT INTO board_cards (board_id, id, type, ref_id, text, x, y, color) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
      .bind(id, c.id.slice(0, 40), c.type, c.ref_id ?? null, (c.text ?? "").slice(0, 2000), Number(c.x) || 0, Number(c.y) || 0, c.color ?? 0)),
    ...edges.map((e) => env.DB.prepare("INSERT OR IGNORE INTO board_edges (board_id, a, b) VALUES (?, ?, ?)").bind(id, e.a, e.b)),
  ];
  await env.DB.batch(stmts);
  return json({ ok: true });
}

export async function deleteBoard(id: string, env: Env): Promise<Response> {
  await env.DB.prepare("UPDATE boards SET deleted_at = datetime('now') WHERE id = ?").bind(id).run();
  return json({ ok: true });
}

/**
 * Swan adds ideas to a board: reads its title and every card, returns 4 to 6 new short sticky ideas, each attached
 * to the card it grows from (so the app can place it nearby and draw the line).
 */
export async function suggestCards(id: string, req: Request, env: Env): Promise<Response> {
  const body = (await req.json().catch(() => ({}))) as { focus?: string };
  const board = await getBoard(id, env).then((r) => r.json()) as { title?: string; cards?: { id: string; type: string; text: string; title: string | null }[]; error?: string };
  if (board.error) return json(board, 404);
  const cards = board.cards ?? [];
  const listed = cards.map((c) => `${c.id}: ${c.type === "text" ? c.text : c.title ?? c.text}`).join("\n") || "(empty board)";
  const out = (await env.AI.run(WRITER, {
    messages: [
      { role: "system", content: `You are Swan, a brainstorming partner. The user has a board of idea cards. Add 4 to 6 NEW ideas that push it further: fresh angles, missing pieces, bold options. Each idea is at most 12 words.
Reply with JSON only: {"ideas":[{"text":"...","from":"<id of the card it grows from, or null>"}]}. No em dashes.` },
      { role: "user", content: `Board: ${board.title || "untitled"}${body.focus ? `\nFocus on: ${body.focus}` : ""}\nCards:\n${listed}` },
    ],
    max_tokens: 2000, reasoning: { effort: "low" }, response_format: { type: "json_object" },
  } as never)) as { response?: string | object; choices?: { message: { content: string } }[] };
  const raw = out.response ?? out.choices?.[0]?.message?.content ?? "";
  let ideas: { text: string; from: string | null }[] = [];
  const known = new Set(cards.map((c) => c.id));
  try {
    const parsed = (typeof raw === "string" ? JSON.parse(raw.slice(raw.indexOf("{"), raw.lastIndexOf("}") + 1)) : raw) as { ideas?: unknown };
    ideas = (Array.isArray(parsed.ideas) ? parsed.ideas : [])
      .map((i) => i as { text?: unknown; from?: unknown })
      .filter((i) => typeof i.text === "string" && i.text.trim())
      .map((i) => ({ text: String(i.text).trim().replace(/—/g, ",").slice(0, 200), from: typeof i.from === "string" && known.has(i.from) ? i.from : null }))
      .slice(0, 6);
  } catch {
    // A reply cut off mid-JSON still has whole ideas in it; keep those.
    const text = typeof raw === "string" ? raw : "";
    for (const m of text.matchAll(/"text"\s*:\s*"((?:[^"\\]|\\.)+)"\s*,\s*"from"\s*:\s*(?:"([^"]*)"|null)/g)) {
      ideas.push({ text: m[1].replace(/\u2014/g, ",").slice(0, 200), from: m[2] && known.has(m[2]) ? m[2] : null });
    }
    ideas = ideas.slice(0, 6);
  }
  if (!ideas.length) return json({ error: "Swan came up empty, try again" }, 502);
  return json({ ideas });
}
