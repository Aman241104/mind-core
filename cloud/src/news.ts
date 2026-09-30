// University/scholarship news for the MS-abroad shortlist, fed by watched pages checked from the laptop.
import type { Env } from "./index.ts";

const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });

async function sha1(text: string): Promise<string> {
  const buf = await crypto.subtle.digest("SHA-1", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(buf)).map((b) => b.toString(16).padStart(2, "0")).join("").slice(0, 16);
}

export async function listNews(url: URL, env: Env): Promise<Response> {
  const unseenOnly = url.searchParams.get("unseen") === "1";
  const rows = unseenOnly
    ? await env.DB.prepare("SELECT * FROM uni_news WHERE seen = 0 ORDER BY detected_at DESC LIMIT 100").all()
    : await env.DB.prepare("SELECT * FROM uni_news ORDER BY detected_at DESC LIMIT 100").all();
  return json({ news: rows.results });
}

export async function markSeen(id: string, env: Env): Promise<Response> {
  await env.DB.prepare("UPDATE uni_news SET seen = 1 WHERE id = ?").bind(id).run();
  return json({ ok: true });
}

type NewsItem = { university: string; country: string; source_url: string; headline: string; summary?: string; kind?: string };

/** POST /v1/brain/news/ingest — pushed by the laptop's monitor-check loop, not the phone. */
export async function ingestNews(req: Request, env: Env): Promise<Response> {
  const body = (await req.json()) as { items?: NewsItem[] };
  let added = 0;
  for (const it of body.items ?? []) {
    const id = await sha1(it.source_url + it.headline);
    const exists = await env.DB.prepare("SELECT 1 FROM uni_news WHERE id = ?").bind(id).first();
    if (exists) continue;
    await env.DB.prepare(
      "INSERT INTO uni_news (id, university, country, source_url, headline, summary, kind) VALUES (?, ?, ?, ?, ?, ?, ?)",
    ).bind(id, it.university, it.country, it.source_url, it.headline, it.summary ?? null, it.kind ?? "update").run();
    added++;
  }
  return json({ added, skipped: (body.items?.length ?? 0) - added });
}
