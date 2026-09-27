// Graph view data: every item and note as a node; edges from saves (mentioned together), your [[links]],
// related-by-meaning note links, and "similar" item pairs found in the meaning index.
import type { Env } from "./index.ts";

const SIMILAR_MIN = 0.72; // bge-m3 cosine; below this "similar" is mostly noise
const SIMILAR_PER_ITEM = 4;

const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });

export async function graph(env: Env): Promise<Response> {
  const [items, notes, rel, links] = await Promise.all([
    env.DB.prepare("SELECT id, name, kind, trust, favorite FROM items WHERE shelf = 'learning'").all<{ id: string; name: string; kind: string; trust: string; favorite: number }>(),
    env.DB.prepare("SELECT id, title, kind FROM notes WHERE deleted_at IS NULL").all<{ id: string; title: string; kind: string }>(),
    env.DB.prepare("SELECT a, b, type FROM relations").all<{ a: string; b: string; type: string }>(),
    env.DB.prepare("SELECT src_id, dst_id, type, score FROM note_links").all<{ src_id: string; dst_id: string; type: string; score: number | null }>(),
  ]);
  const nodes = [
    ...items.results.map((i) => ({ id: i.id, label: i.name, kind: i.kind, group: "item", trust: i.trust, favorite: !!i.favorite })),
    ...notes.results.map((n) => ({ id: n.id, label: n.title || `Untitled ${n.kind}`, kind: n.kind, group: "note", trust: null, favorite: false })),
  ];
  const known = new Set(nodes.map((n) => n.id));
  const all = [
    ...rel.results.map((r) => ({ a: r.a, b: r.b, type: r.type })),
    ...links.results.map((l) => ({ a: l.src_id, b: l.dst_id, type: l.type })),
  ].filter((e) => known.has(e.a) && known.has(e.b) && e.a !== e.b);
  // One edge per pair: the strongest reason wins (your own link > saved together > similar meaning).
  const rank: Record<string, number> = { mention: 0, mentioned_together: 1, related: 2, similar: 3 };
  const best = new Map<string, { a: string; b: string; type: string }>();
  for (const e of all) {
    const key = e.a < e.b ? `${e.a}|${e.b}` : `${e.b}|${e.a}`;
    const had = best.get(key);
    if (!had || (rank[e.type] ?? 9) < (rank[had.type] ?? 9)) best.set(key, e);
  }
  const edges = [...best.values()];
  return json({ nodes, edges });
}

/**
 * Find "similar" pairs for items not checked yet, a batch at a time (the cron calls this every 5 minutes, so new
 * items get linked soon after they're saved). Stored as relations of type 'similar'.
 */
export async function linkSimilar(env: Env, batch = 12): Promise<{ checked: number; linked: number }> {
  const todo = await env.DB.prepare("SELECT id FROM items WHERE shelf = 'learning' AND linked_at IS NULL LIMIT ?")
    .bind(batch).all<{ id: string }>();
  const ids = todo.results.map((r) => r.id);
  if (!ids.length) return { checked: 0, linked: 0 };
  const vectors = await env.VEC.getByIds(ids);
  const stmts: D1PreparedStatement[] = [];
  let linked = 0;
  for (const v of vectors) {
    const hits = await env.VEC.query(v.values as number[], { topK: SIMILAR_PER_ITEM + 1, filter: { shelf: "learning" } });
    for (const h of hits.matches) {
      if (h.id === v.id || h.score < SIMILAR_MIN) continue;
      const [a, b] = [v.id, h.id].sort();
      stmts.push(env.DB.prepare("INSERT OR IGNORE INTO relations (a, b, type) SELECT ?1, ?2, 'similar' WHERE EXISTS (SELECT 1 FROM items WHERE id = ?1) AND EXISTS (SELECT 1 FROM items WHERE id = ?2)").bind(a, b));
      linked++;
    }
  }
  // Mark every item in the batch (even ones with no vector yet), so each is checked once.
  for (const id of ids) stmts.push(env.DB.prepare("UPDATE items SET linked_at = datetime('now') WHERE id = ?").bind(id));
  await env.DB.batch(stmts);
  return { checked: ids.length, linked };
}
