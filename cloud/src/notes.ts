// P2: notes and ideas. Saved as markdown; indexed for search and Ask; linked by [[mentions]] and by meaning.
import type { Env } from "./index.ts";
import { isOwnAudio, transcribe } from "./voice.ts";

const EMBED_MODEL = "@cf/baai/bge-m3";
const WRITER = "@cf/openai/gpt-oss-120b";
const STAGES = new Set(["spark", "growing", "ready", "done", "parked"]);
const MAX_BODY = 100_000;
const RELATED_MIN_SCORE = 0.55;

const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });

interface NoteRow {
  id: string; kind: string; title: string; body: string; stage: string | null; color: number; pinned: number;
  favorite: number; voice_url: string | null; source: string; created_at: string; updated_at: string;
}

function newId(): string {
  return [...crypto.getRandomValues(new Uint8Array(8))].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** List view: no full body, but a preview and the numbers the cards show. */
function summarize(n: NoteRow) {
  const words = n.body.trim() ? n.body.trim().split(/\s+/).length : 0;
  const tasks = n.body.match(/^\s*[-*] \[( |x|X)\]/gm) ?? [];
  const done = tasks.filter((t) => /\[(x|X)\]/.test(t)).length;
  const preview = n.body.replace(/^#+\s*/gm, "").replace(/^\s*[-*] \[( |x|X)\]\s*/gm, "").replace(/\[\[([^\]]+)\]\]/g, "$1")
    .replace(/\s+/g, " ").trim().slice(0, 220);
  const { body: _body, ...rest } = n;
  return { ...rest, pinned: !!n.pinned, favorite: !!n.favorite, preview, words, tasks: tasks.length, tasks_done: done };
}

export async function listNotes(url: URL, env: Env): Promise<Response> {
  const kind = url.searchParams.get("kind");
  const stage = url.searchParams.get("stage");
  const trash = url.searchParams.get("trash") === "1";
  const where = [trash ? "deleted_at IS NOT NULL" : "deleted_at IS NULL"];
  const args: unknown[] = [];
  if (kind) { where.push("kind = ?"); args.push(kind); }
  if (stage) { where.push("stage = ?"); args.push(stage); }
  const rows = await env.DB.prepare(
    `SELECT * FROM notes WHERE ${where.join(" AND ")} ORDER BY pinned DESC, updated_at DESC LIMIT 500`,
  ).bind(...args).all<NoteRow>();
  return json(rows.results.map(summarize));
}

export async function getNote(id: string, env: Env): Promise<Response> {
  const n = await env.DB.prepare("SELECT * FROM notes WHERE id = ?").bind(id).first<NoteRow>();
  if (!n) return json({ error: "no such note" }, 404);
  // Backlinks: notes that mention or relate to this one. Links out: notes and items, with names for the chips.
  const [back, out] = await Promise.all([
    env.DB.prepare(
      `SELECT n.id, n.kind, n.title, l.type FROM note_links l JOIN notes n ON n.id = l.src_id
       WHERE l.dst_id = ? AND n.deleted_at IS NULL ORDER BY l.type, n.updated_at DESC LIMIT 20`,
    ).bind(id).all(),
    env.DB.prepare(
      `SELECT l.dst_id AS id, l.dst_type, l.type, l.score,
         COALESCE(n.title, i.name) AS title, COALESCE(n.kind, i.kind) AS kind
       FROM note_links l LEFT JOIN notes n ON l.dst_type = 'note' AND n.id = l.dst_id AND n.deleted_at IS NULL
       LEFT JOIN items i ON l.dst_type = 'item' AND i.id = l.dst_id
       WHERE l.src_id = ? ORDER BY l.type, l.score DESC LIMIT 20`,
    ).bind(id).all(),
  ]);
  return json({ ...summarize(n), body: n.body, backlinks: back.results, links: out.results.filter((r) => r.title) });
}

interface NoteIn { kind?: string; title?: string; body?: string; stage?: string | null; color?: number; pinned?: boolean; favorite?: boolean; source?: string }

function clean(b: NoteIn) {
  return {
    kind: b.kind === "idea" ? "idea" : b.kind === "note" ? "note" : undefined,
    title: b.title?.slice(0, 300),
    body: b.body?.slice(0, MAX_BODY),
    stage: b.stage === null ? null : b.stage && STAGES.has(b.stage) ? b.stage : undefined,
    color: typeof b.color === "number" ? Math.max(0, Math.min(9, Math.floor(b.color))) : undefined,
    pinned: b.pinned, favorite: b.favorite,
  };
}

export async function createNote(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const b = clean((await req.json()) as NoteIn);
  const id = newId();
  const kind = b.kind ?? "note";
  await env.DB.prepare(
    `INSERT INTO notes (id, kind, title, body, stage, color, pinned, favorite, source) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  ).bind(id, kind, b.title ?? "", b.body ?? "", kind === "idea" ? (b.stage ?? "spark") : null, b.color ?? 0,
    b.pinned ? 1 : 0, b.favorite ? 1 : 0, "app").run();
  ctx.waitUntil(reindexNote(env, id)); // search, Ask and links update in the background
  return getNote(id, env);
}

export async function updateNote(id: string, req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const b = clean((await req.json()) as NoteIn);
  const sets: string[] = [];
  const args: unknown[] = [];
  const put = (col: string, v: unknown) => { sets.push(`${col} = ?`); args.push(v); };
  if (b.kind) put("kind", b.kind);
  if (b.title !== undefined) put("title", b.title);
  if (b.body !== undefined) put("body", b.body);
  if (b.stage !== undefined) put("stage", b.stage);
  if (b.color !== undefined) put("color", b.color);
  if (b.pinned !== undefined) put("pinned", b.pinned ? 1 : 0);
  if (b.favorite !== undefined) put("favorite", b.favorite ? 1 : 0);
  if (!sets.length) return getNote(id, env);
  const r = await env.DB.prepare(`UPDATE notes SET ${sets.join(", ")}, updated_at = datetime('now') WHERE id = ?`).bind(...args, id).run();
  if (!r.meta.changes) return json({ error: "no such note" }, 404);
  if (b.title !== undefined || b.body !== undefined) ctx.waitUntil(reindexNote(env, id));
  return getNote(id, env);
}

/** To the trash (restorable for 30 days); `?restore=1` brings it back. */
export async function deleteNote(id: string, url: URL, env: Env): Promise<Response> {
  const restore = url.searchParams.get("restore") === "1";
  await env.DB.prepare(`UPDATE notes SET deleted_at = ${restore ? "NULL" : "datetime('now')"} WHERE id = ?`).bind(id).run();
  return json({ ok: true });
}

/**
 * A voice note straight into a note: transcribe, then (unless raw) tidy the ramble into a titled note with
 * bullets and checklists, keeping every fact and the speaker's words where it matters.
 */
export async function noteFromVoice(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const body = (await req.json()) as { voice_url?: string; kind?: string; raw?: boolean };
  if (!body.voice_url || !isOwnAudio(env, body.voice_url)) return json({ error: "voice_url must be your Cloudinary audio" }, 400);
  const heard = await transcribe(env, body.voice_url);
  if (!heard) return json({ error: "couldn't hear anything in that recording" }, 422);
  let title = heard.split(/[.!?]/)[0].slice(0, 60);
  let text = heard;
  if (!body.raw) {
    const out = (await env.AI.run(WRITER, {
      messages: [
        { role: "system", content: `Turn this voice memo into a clean ${body.kind === "idea" ? "idea" : "note"} in markdown. First line: a short title (no #). Then the content: short paragraphs or bullets; things to do become "- [ ] ..." lines. Keep every fact, name, number and date. Don't add anything that wasn't said. Same language as the speaker. No em dashes.` },
        { role: "user", content: heard.slice(0, 8000) },
      ],
      max_tokens: 1200,
    } as never)) as { response?: string; choices?: { message: { content: string } }[] };
    const tidy = (out.response ?? out.choices?.[0]?.message?.content ?? "").trim();
    if (tidy) {
      const [first, ...rest] = tidy.split("\n");
      title = first.replace(/^#+\s*/, "").trim().slice(0, 120) || title;
      text = rest.join("\n").trim() + `\n\n> Voice memo: ${heard}`;
    }
  }
  const id = newId();
  const kind = body.kind === "idea" ? "idea" : "note";
  await env.DB.prepare(
    `INSERT INTO notes (id, kind, title, body, stage, voice_url, source) VALUES (?, ?, ?, ?, ?, ?, 'voice')`,
  ).bind(id, kind, title, text.slice(0, MAX_BODY), kind === "idea" ? "spark" : null, body.voice_url).run();
  ctx.waitUntil(reindexNote(env, id));
  return getNote(id, env);
}

// ---------- indexing and links ----------

function noteChunks(n: NoteRow): string[] {
  const text = `${n.title}\n\n${n.body}`.replace(/\s+/g, " ").trim();
  const out: string[] = [];
  for (let i = 0; i < text.length && out.length < 12; i += 780) out.push(text.slice(i, i + 900));
  return out.filter((c) => c.length > 8);
}

/** Re-embed a note, refresh its search entry, its [[mentions]] and its related links. */
export async function reindexNote(env: Env, id: string): Promise<void> {
  const n = await env.DB.prepare("SELECT * FROM notes WHERE id = ?").bind(id).first<NoteRow>();
  if (!n) return;
  const chunks = noteChunks(n);
  await env.DB.batch([
    env.DB.prepare("DELETE FROM notes_fts WHERE note_id = ?").bind(id),
    env.DB.prepare("INSERT INTO notes_fts (note_id, title, body) VALUES (?, ?, ?)").bind(id, n.title, n.body),
  ]);
  // Old chunk vectors beyond the new count would linger; delete the full possible range first.
  await env.VEC_CHUNKS.deleteByIds(Array.from({ length: 12 }, (_, k) => `n:${id}:${k}`));
  if (!chunks.length) return;
  const emb = (await env.AI.run(EMBED_MODEL, { text: chunks })) as { data: number[][] };
  await env.VEC_CHUNKS.upsert(emb.data.map((values, k) => ({
    id: `n:${id}:${k}`, values, metadata: { shelf: "note", note_id: id, kind: n.kind, t: chunks[k] },
  })));

  // [[Mentions]] → notes by title, else items by name.
  const mentions = [...new Set([...n.body.matchAll(/\[\[([^\]]{1,200})\]\]/g)].map((m) => m[1].trim().toLowerCase()))];
  const links: D1PreparedStatement[] = [env.DB.prepare("DELETE FROM note_links WHERE src_id = ?").bind(id)];
  for (const name of mentions) {
    const hit = await env.DB.prepare(
      `SELECT id, 'note' AS t FROM notes WHERE lower(title) = ? AND id != ? AND deleted_at IS NULL
       UNION ALL SELECT id, 'item' FROM items WHERE lower(name) = ? LIMIT 1`,
    ).bind(name, id, name).first<{ id: string; t: string }>();
    if (hit) links.push(env.DB.prepare("INSERT OR IGNORE INTO note_links VALUES (?, ?, ?, 'mention', NULL)").bind(id, hit.id, hit.t));
  }
  // Related by meaning: the closest saved items and other notes.
  const v = emb.data[0];
  const [items, notes] = await Promise.all([
    env.VEC.query(v, { topK: 5 }),
    env.VEC_CHUNKS.query(v, { topK: 12, filter: { shelf: "note" }, returnMetadata: "all" }),
  ]);
  for (const m of items.matches) if (m.score >= RELATED_MIN_SCORE) {
    links.push(env.DB.prepare("INSERT OR IGNORE INTO note_links VALUES (?, ?, 'item', 'related', ?)").bind(id, m.id, m.score));
  }
  const seen = new Set<string>();
  for (const m of notes.matches) {
    const other = String(m.metadata?.note_id ?? "");
    if (!other || other === id || seen.has(other) || m.score < RELATED_MIN_SCORE || seen.size >= 5) continue;
    seen.add(other);
    links.push(env.DB.prepare("INSERT OR IGNORE INTO note_links VALUES (?, ?, 'note', 'related', ?)").bind(id, other, m.score));
  }
  await env.DB.batch(links);
}
