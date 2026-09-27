// M3 "Ask": grounded answers over your saves, plus a research queue the laptop answers with web search.
import type { Env } from "./index.ts";

const EMBED_MODEL = "@cf/baai/bge-m3";
const RERANK_MODEL = "@cf/baai/bge-reranker-base";
// Tested 2026-09-27 on the free plan: 2.4 s, cited correctly, refused an off-library question.
const ANSWER_MODEL = "@cf/openai/gpt-oss-120b";

const CHUNK_CHARS = 900;
const CHUNK_OVERLAP = 120;
const MAX_CHUNKS_PER_SAVE = 8;

const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });

// ---------- indexing ----------

interface SaveText {
  id: string; shelf: string; kind_hint: string; note: string | null; caption: string | null;
  transcript: string | null; screen_text: string | null;
}

export function chunks(s: SaveText): string[] {
  const text = [s.note, s.caption, s.transcript, s.screen_text].filter(Boolean).join("\n\n").replace(/\s+/g, " ").trim();
  const out: string[] = [];
  for (let i = 0; i < text.length && out.length < MAX_CHUNKS_PER_SAVE; i += CHUNK_CHARS - CHUNK_OVERLAP) {
    out.push(text.slice(i, i + CHUNK_CHARS));
  }
  return out.filter((c) => c.length > 40);
}

/** Embed a save's text so Ask can quote it. Work saves are never indexed. */
export async function indexSaves(env: Env, saveIds: string[]): Promise<number> {
  if (!saveIds.length) return 0;
  const rows = await env.DB.prepare(
    `SELECT id, shelf, kind_hint, note, caption, transcript, screen_text FROM saves
     WHERE id IN (${saveIds.map(() => "?").join(",")}) AND shelf != 'work'`,
  ).bind(...saveIds).all<SaveText>();
  const vectors: VectorizeVector[] = [];
  const texts: string[] = [];
  for (const s of rows.results) {
    chunks(s).forEach((c, n) => {
      texts.push(c);
      vectors.push({ id: `${s.id}:${n}`, values: [], metadata: { shelf: s.shelf, save_id: s.id, t: c } });
    });
  }
  for (let i = 0; i < texts.length; i += 50) {
    const out = (await env.AI.run(EMBED_MODEL, { text: texts.slice(i, i + 50) })) as { data: number[][] };
    out.data.forEach((v, j) => (vectors[i + j].values = v));
  }
  if (vectors.length) await env.VEC_CHUNKS.upsert(vectors);
  await env.DB.prepare(`UPDATE saves SET indexed = 1 WHERE id IN (${saveIds.map(() => "?").join(",")})`).bind(...saveIds).run();
  return vectors.length;
}

/** Backfill: index a few done-but-unindexed saves per call (the brain calls this in a loop). */
export async function reindex(env: Env): Promise<Response> {
  const rows = await env.DB.prepare(
    "SELECT id FROM saves WHERE status = 'done' AND indexed = 0 AND shelf != 'work' LIMIT 15",
  ).all<{ id: string }>();
  const n = await indexSaves(env, rows.results.map((r) => r.id));
  const left = await env.DB.prepare(
    "SELECT COUNT(*) AS n FROM saves WHERE status = 'done' AND indexed = 0 AND shelf != 'work'",
  ).first<{ n: number }>();
  return json({ saves: rows.results.length, chunks: n, left: left?.n ?? 0 });
}

// ---------- ask ----------

interface Turn { role: "user" | "assistant"; content: string }
interface Candidate { key: string; text: string; source: SourceOut }
interface SourceOut {
  n?: number; type: "item" | "save" | "note"; item_id?: string; save_id?: string; note_id?: string; title: string; url?: string | null;
  kind?: string; trust?: string;
}

const RECENT = /\b(today|this week|last week|recent(ly)?|latest|new(est)?|this month|yesterday)\b/i;
// "What jobs did I save?" is about a type: look at every item of that type, not just the top matches.
const KINDS: [RegExp, string][] = [
  [/\bjobs?\b|hiring|openings?|roles?\b/i, "job"], [/\bcourses?\b/i, "course"], [/\bcert(ificat(e|ion)s?|s)?\b/i, "cert"],
  [/\brepos?(itor(y|ies))?\b/i, "repo"], [/\btools?\b/i, "tool"], [/\btips?\b/i, "tip"],
  [/\bvideos?\b/i, "video"], [/\bplaylists?\b/i, "playlist"], [/\bbooks?\b|\bpdfs?\b/i, "book"],
];

export async function ask(req: Request, env: Env, hybridItems: (q: string, limit: number) => Promise<Record<string, unknown>[]>): Promise<Response> {
  const body = (await req.json()) as { q?: string; history?: Turn[]; stream?: boolean };
  const q = body.q?.trim();
  if (!q) return json({ error: "empty question" }, 400);

  const candidates: Candidate[] = [];
  const itemSource = (it: Record<string, unknown>): Candidate => {
    const v = it.verification ? (typeof it.verification === "string" ? JSON.parse(it.verification) : it.verification) as Record<string, unknown> : null;
    const facts = v?.repo ? ` GitHub ${v.repo}, ${v.stars ?? "?"} stars, license ${v.license ?? "?"}, last update ${String(v.pushed_at ?? "?").slice(0, 10)}.` : "";
    return {
      key: `i:${it.id}`,
      text: `${it.name} (${it.kind}, trust: ${it.trust}, your status: ${it.status}, saved ${String(it.created_at).slice(0, 10)}). ${it.one_line ?? ""}${facts}`,
      source: { type: "item", item_id: String(it.id), title: String(it.name), url: (it.url as string) ?? null, kind: String(it.kind), trust: String(it.trust) },
    };
  };

  // "What did I save this week?" is about time, not meaning: take the newest items directly.
  const recentMode = RECENT.test(q);
  if (recentMode) {
    const days = /today|yesterday/i.test(q) ? 2 : /month/i.test(q) ? 31 : 8;
    const recent = await env.DB.prepare(
      `SELECT * FROM items WHERE shelf = 'learning' AND created_at > datetime('now', ?) ORDER BY created_at DESC LIMIT 25`,
    ).bind(`-${days} days`).all();
    recent.results.forEach((it) => candidates.push(itemSource(it)));
  }

  const kind = KINDS.find(([re]) => re.test(q))?.[1];
  if (kind) {
    const all = await env.DB.prepare(
      `SELECT * FROM items WHERE shelf = 'learning' AND kind = ? ORDER BY created_at DESC LIMIT 25`,
    ).bind(kind).all();
    all.results.forEach((it) => { if (!candidates.some((c) => c.key === `i:${it.id}`)) candidates.push(itemSource(it)); });
  }
  const listMode = recentMode || !!kind;

  // Meaning + keyword search over items, and meaning search over the saved text itself.
  const emb = (await env.AI.run(EMBED_MODEL, { text: [q] })) as { data: number[][] };
  const [items, chunkHits] = await Promise.all([
    hybridItems(q, 12),
    env.VEC_CHUNKS.query(emb.data[0], { topK: 12, filter: { shelf: { $ne: "work" } }, returnMetadata: "all" }),
  ]);
  items.forEach((it) => { if (!candidates.some((c) => c.key === `i:${it.id}`)) candidates.push(itemSource(it)); });
  // GraphRAG-lite: things connected to the best matches (saved together, or similar in meaning) come along too;
  // the reranker drops the ones that don't help.
  const seeds = items.slice(0, 5).map((it) => String(it.id));
  if (seeds.length && !listMode) {
    const ph = seeds.map(() => "?").join(",");
    const near = await env.DB.prepare(
      `SELECT i.* FROM relations r JOIN items i ON i.id = CASE WHEN r.a IN (${ph}) THEN r.b ELSE r.a END
       WHERE (r.a IN (${ph}) OR r.b IN (${ph})) AND r.type IN ('mentioned_together', 'similar') AND i.shelf = 'learning' LIMIT 10`,
    ).bind(...seeds, ...seeds, ...seeds).all();
    near.results.forEach((it) => { if (!candidates.some((c) => c.key === `i:${it.id}`)) candidates.push(itemSource(it)); });
  }

  const saveIds = [...new Set(chunkHits.matches.map((m) => String(m.metadata?.save_id)))].filter(Boolean);
  const saves = saveIds.length
    ? await env.DB.prepare(`SELECT id, url, creator, kind_hint, saved_at FROM saves WHERE id IN (${saveIds.map(() => "?").join(",")})`)
      .bind(...saveIds).all<{ id: string; url: string | null; creator: string | null; kind_hint: string; saved_at: string }>()
    : { results: [] };
  const saveById = new Map(saves.results.map((s) => [s.id, s]));
  // Your own notes and ideas.
  const noteIds = [...new Set(chunkHits.matches.map((m) => String(m.metadata?.note_id ?? "")))].filter(Boolean);
  const notes = noteIds.length
    ? await env.DB.prepare(`SELECT id, kind, title, updated_at FROM notes WHERE deleted_at IS NULL AND id IN (${noteIds.map(() => "?").join(",")})`)
      .bind(...noteIds).all<{ id: string; kind: string; title: string; updated_at: string }>()
    : { results: [] };
  const noteById = new Map(notes.results.map((n) => [n.id, n]));
  for (const m of chunkHits.matches) {
    const nid = String(m.metadata?.note_id ?? "");
    const n = nid ? noteById.get(nid) : undefined;
    if (n) {
      candidates.push({
        key: `c:${m.id}`,
        text: `From your ${n.kind} "${n.title || "untitled"}" (edited ${n.updated_at.slice(0, 10)}): ${String(m.metadata?.t ?? "")}`,
        source: { type: "note", note_id: n.id, title: n.title || "Untitled " + n.kind, kind: n.kind },
      });
      continue;
    }
    const s = saveById.get(String(m.metadata?.save_id));
    if (!s) continue;
    const who = s.creator ? ` by ${s.creator}` : "";
    candidates.push({
      key: `c:${m.id}`,
      text: `From a saved ${s.kind_hint}${who} (saved ${s.saved_at.slice(0, 10)}): ${String(m.metadata?.t ?? "")}`,
      source: { type: "save", save_id: s.id, title: `${s.creator ?? s.kind_hint} · ${s.kind_hint}`, url: s.url, kind: s.kind_hint },
    });
  }
  if (!candidates.length) {
    const none = "I couldn't find anything about that in your saves.";
    if (body.stream) return ndjson([{ type: "sources", sources: [] }, { type: "delta", text: none }, { type: "done", cited: [], found: false, grounded: true }]);
    return json({ answer: none, sources: [], found: false });
  }

  // Add what the posts claimed (salaries, star counts, "free"...), marked as unverified.
  const itemIds = candidates.filter((c) => c.source.type === "item").map((c) => c.source.item_id!);
  if (itemIds.length) {
    const claims = await env.DB.prepare(
      `SELECT item_id, group_concat(claims, ' ') AS c FROM item_sources WHERE item_id IN (${itemIds.map(() => "?").join(",")}) GROUP BY item_id`,
    ).bind(...itemIds).all<{ item_id: string; c: string }>();
    const byItem = new Map(claims.results.map((r) => [r.item_id, r.c]));
    for (const c of candidates) {
      const raw = c.source.item_id ? byItem.get(c.source.item_id) : undefined;
      const list = raw ? raw.match(/"((?:[^"\\]|\\.)*)"/g)?.map((x) => x.slice(1, -1)) ?? [] : [];
      if (list.length) c.text += ` Claims in the post (unverified): ${list.slice(0, 6).join("; ")}.`;
    }
  }

  // Rerank everything against the question and keep the best few (less noise in, fewer made-up answers out).
  let top = candidates;
  if (!listMode && candidates.length > 8) {
    // The reranker isn't in workers-types' model list yet, so call it through a loose type.
    const ai = env.AI as unknown as { run(model: string, input: unknown): Promise<unknown> };
    const ranked = (await ai.run(RERANK_MODEL, {
      query: q, contexts: candidates.map((c) => ({ text: c.text.slice(0, 1500) })),
    })) as unknown as { response: { id: number; score: number }[] };
    top = ranked.response.sort((a, b) => b.score - a.score).slice(0, 8).map((r) => candidates[r.id]);
  }
  top = top.slice(0, listMode ? 30 : 8);
  const context = top.map((c, i) => `[${i + 1}] ${c.text}`).join("\n");

  const system = `You are mind-core, the user's assistant for their own notes and ideas and the things they saved from reels, posts and screenshots.
Answer ONLY from the numbered sources. Cite every fact like [1] or [2][3], right after the fact.
If the sources don't answer the question, say "That isn't in your saves." and stop; don't use outside knowledge.
Sources marked trust: check or unconfirmed contain claims nobody has verified; say so when you use them.
Be concise: short paragraphs or a short list. Plain words, no marketing tone, no em dashes.`;
  const history = (body.history ?? []).slice(-6).map((t) => ({ role: t.role, content: t.content.slice(0, 2000) }));
  const messages = [
    { role: "system", content: system },
    ...history,
    { role: "user", content: `Sources:\n${context}\n\nQuestion: ${q}` },
  ];
  if (body.stream) return streamAnswer(env, messages, top);
  const out = (await env.AI.run(ANSWER_MODEL, {
    messages, max_tokens: 2000, reasoning: { effort: "low" },
  } as never)) as { response?: string; choices?: { message: { content: string } }[] };
  let answer = (out.response ?? out.choices?.[0]?.message?.content ?? "").trim();
  // gpt-oss sometimes uses 【1】; normalize to [1].
  answer = answer.replace(/【(\d+)(?:†[^】]*)?】/g, "[$1]");

  // Mechanical citation check: only numbers that exist count; an answer with no valid citation is flagged.
  const cited = [...new Set([...answer.matchAll(/\[(\d+)\]/g)].map((m) => Number(m[1])))].filter((n) => n >= 1 && n <= top.length);
  const refused = /isn.t in your saves|not in your saves/i.test(answer);
  return json({
    answer,
    sources: cited.map((n) => ({ ...top[n - 1].source, n })),
    found: !refused,
    grounded: refused || cited.length > 0,
  });
}

// ---------- streaming ----------

function ndjson(lines: unknown[]): Response {
  return new Response(lines.map((l) => JSON.stringify(l)).join("\n") + "\n", { headers: { "content-type": "application/x-ndjson" } });
}

/**
 * The answer as it's written: one JSON object per line. First {type:"sources"} (every numbered source, so the app
 * can show [n] chips as they appear), then {type:"delta",text} pieces, then {type:"done",cited,found,grounded}.
 */
function streamAnswer(env: Env, messages: unknown[], top: Candidate[]): Response {
  const { readable, writable } = new TransformStream<Uint8Array, Uint8Array>();
  const w = writable.getWriter();
  const enc = new TextEncoder();
  const send = (o: unknown) => w.write(enc.encode(JSON.stringify(o) + "\n"));
  (async () => {
    let answer = "";
    try {
      await send({ type: "sources", sources: top.map((c, i) => ({ ...c.source, n: i + 1 })) });
      const stream = (await env.AI.run(ANSWER_MODEL, {
        messages, max_tokens: 2000, stream: true, reasoning: { effort: "low" },
      } as never)) as unknown as ReadableStream<Uint8Array>;
      const reader = stream.pipeThrough(new TextDecoderStream()).getReader();
      let buf = "";
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        buf += value;
        let nl: number;
        while ((nl = buf.indexOf("\n")) >= 0) {
          const line = buf.slice(0, nl).trim();
          buf = buf.slice(nl + 1);
          if (!line.startsWith("data:")) continue;
          const data = line.slice(5).trim();
          if (data === "[DONE]") continue;
          let piece = "";
          try {
            const o = JSON.parse(data) as { response?: string; choices?: { delta?: { content?: string } }[] };
            piece = o.response ?? o.choices?.[0]?.delta?.content ?? "";
          } catch { continue; }
          if (!piece) continue;
          piece = piece.replace(/【(\d+)(?:†[^】]*)?】/g, "[$1]");
          answer += piece;
          await send({ type: "delta", text: piece });
        }
      }
      const cited = [...new Set([...answer.matchAll(/\[(\d+)\]/g)].map((m) => Number(m[1])))].filter((n) => n >= 1 && n <= top.length);
      const refused = /isn.t in your saves|not in your saves/i.test(answer);
      await send({ type: "done", cited, found: !refused, grounded: refused || cited.length > 0 });
    } catch (e) {
      await send({ type: "error", error: e instanceof Error ? e.message : String(e) });
    } finally {
      await w.close();
    }
  })();
  return new Response(readable, { headers: { "content-type": "application/x-ndjson", "cache-control": "no-store" } });
}

// ---------- research (answered by the laptop's Claude with web search) ----------

export async function createResearch(req: Request, env: Env): Promise<Response> {
  const body = (await req.json()) as { question?: string };
  const question = body.question?.trim();
  if (!question) return json({ error: "empty question" }, 400);
  const row = await env.DB.prepare("INSERT INTO research (question) VALUES (?) RETURNING id").bind(question.slice(0, 2000))
    .first<{ id: number }>();
  const brain = await env.DB.prepare(
    "SELECT (strftime('%s','now') - strftime('%s', last_seen)) AS age FROM brain WHERE id = 'laptop'",
  ).first<{ age: number }>();
  return json({ id: row!.id, laptop_online: !!brain && brain.age < 120 });
}

export async function getResearch(id: number, env: Env): Promise<Response> {
  const r = await env.DB.prepare("SELECT id, question, status, answer, sources, error, created_at, updated_at FROM research WHERE id = ?")
    .bind(id).first<Record<string, unknown>>();
  if (!r) return json({ error: "no such research" }, 404);
  return json({ ...r, sources: r.sources ? JSON.parse(String(r.sources)) : [] });
}

export async function claimResearch(env: Env): Promise<Response> {
  const r = await env.DB.prepare(
    `UPDATE research SET status = 'leased', lease_until = datetime('now', '+15 minutes'), updated_at = datetime('now')
     WHERE id = (SELECT id FROM research WHERE status = 'pending' OR (status = 'leased' AND lease_until < datetime('now'))
                 ORDER BY id LIMIT 1)
     RETURNING id, question, kind, item_id`,
  ).first();
  return json(r ?? null);
}

export async function finishResearch(id: number, req: Request, env: Env): Promise<Response> {
  const body = (await req.json()) as { answer?: string; sources?: unknown[]; error?: string; deadline?: string | null };
  const r = await env.DB.prepare("SELECT kind, item_id FROM research WHERE id = ?").bind(id).first<{ kind: string; item_id: string | null }>();
  if (r?.kind === "deadline" && r.item_id && body.deadline && /^\d{4}-\d{2}-\d{2}$/.test(body.deadline)) {
    await env.DB.prepare("UPDATE items SET deadline = ?, deadline_source = 'research' WHERE id = ?").bind(body.deadline, r.item_id).run();
  }
  await env.DB.prepare(
    `UPDATE research SET status = ?, answer = ?, sources = ?, error = ?, updated_at = datetime('now') WHERE id = ?`,
  ).bind(body.error ? "failed" : "done", body.answer ?? null, JSON.stringify(body.sources ?? []), body.error ?? null, id).run();
  return json({ ok: true });
}
