// P4: resurfacing. A few things worth a second look today: an idea you've let rest (incubation), something you
// meant to try, and an older verified find. The pick changes daily but stays put within a day.
import type { Env } from "./index.ts";
import { todayIST } from "./deadlines.ts";

const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });

// Spaced-out rest periods: an idea comes back after about 3, 7, 21 and 60 days untouched.
const WINDOWS = [[3, 5], [7, 11], [21, 30], [60, 90]];

export async function resurface(env: Env): Promise<Response> {
  const day = todayIST();
  // Daily shuffle that SQLite can do: order by a slice of the id that depends on the date.
  const pos = (Number(day.slice(8, 10)) % 12) + 1;
  const shuffle = `substr(id, ${pos}, 4)`;
  const windowSql = WINDOWS.map(([a, b]) => `(julianday('now') - julianday(updated_at) BETWEEN ${a} AND ${b})`).join(" OR ");
  const [idea, want, gem] = await Promise.all([
    env.DB.prepare(
      `SELECT id, title, kind, stage, color, updated_at FROM notes
       WHERE deleted_at IS NULL AND kind = 'idea' AND COALESCE(stage, 'spark') NOT IN ('done', 'parked') AND (${windowSql})
       ORDER BY ${shuffle} LIMIT 1`,
    ).first<{ id: string; title: string; kind: string; stage: string | null; color: number; updated_at: string }>(),
    env.DB.prepare(
      `SELECT id, name, kind, one_line, updated_at FROM items
       WHERE status IN ('want', 'trying') AND julianday('now') - julianday(updated_at) > 7 ORDER BY ${shuffle} LIMIT 1`,
    ).first<{ id: string; name: string; kind: string; one_line: string | null; updated_at: string }>(),
    // "When you saved it" is the original save date (WhatsApp imports go back months), not when it was imported.
    env.DB.prepare(
      `SELECT i.id, i.name, i.kind, i.one_line, min(replace(replace(s.saved_at, 'T', ' '), 'Z', '')) AS first_saved
       FROM items i JOIN item_sources x ON x.item_id = i.id JOIN saves s ON s.id = x.save_id
       WHERE i.shelf = 'learning' AND i.trust = 'verified' AND i.status = 'new' AND i.favorite = 0
       GROUP BY i.id HAVING julianday('now') - julianday(first_saved) > 14
       ORDER BY substr(i.id, ${pos}, 4) LIMIT 1`,
    ).first<{ id: string; name: string; kind: string; one_line: string | null; first_saved: string }>(),
  ]);
  const ago = (t: string) => Math.max(1, Math.round((Date.now() - Date.parse(t.slice(0, 19).replace(" ", "T") + "Z")) / 86_400_000));
  const out = [];
  if (idea) out.push({ type: "note", id: idea.id, title: idea.title || "Untitled idea", kind: "idea", color: idea.color,
    reason: `Resting for ${ago(idea.updated_at)} days. Fresh eyes?` });
  if (want) out.push({ type: "item", id: want.id, title: want.name, kind: want.kind, subtitle: want.one_line,
    reason: `You wanted to try this ${ago(want.updated_at)} days ago` });
  if (gem) out.push({ type: "item", id: gem.id, title: gem.name, kind: gem.kind, subtitle: gem.one_line,
    reason: `A verified find from ${ago(gem.first_saved)} days ago you haven't tried yet` });
  return json({ day, items: out });
}
