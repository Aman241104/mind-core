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

const NEWS_MODEL = "@cf/openai/gpt-oss-120b";

/**
 * POST /v1/brain/news/webhook?key=... — Firecrawl's monitor webhook, called by Firecrawl's servers
 * (no Bearer auth available), gated by a shared-secret query param instead.
 *
 * Real payload shape (confirmed live via `wrangler tail` against an actual webhook call, not guessed):
 * `{ success, type: "monitor.page" | "monitor.check.completed", id: checkId, data: [...], metadata }`.
 * A `monitor.page` event's `data[]` holds `{ checkId, monitorId, url, status, diff, isMeaningful, judgment }`
 * — `diff` is null on "new" (baseline) pages since there's nothing to compare against yet; only "changed"
 * pages carry real diff text, which is also the only status worth turning into a news item.
 */
export async function ingestWebhook(req: Request, env: Env): Promise<Response> {
  const url = new URL(req.url);
  if (url.searchParams.get("key") !== env.NEWS_WEBHOOK_KEY) return new Response("forbidden", { status: 403 });

  const body = (await req.json().catch(() => ({}))) as Record<string, unknown>;
  console.log("firecrawl webhook raw:", JSON.stringify(body).slice(0, 4000));

  if (body.type === "monitor.check.completed") return json({ added: 0, note: "check-completed event, no page content" });
  if (body.type !== "monitor.page") return json({ added: 0, note: `unrecognized event type: ${body.type}` });

  const pages = (body.data as unknown[] | undefined) ?? [];

  let added = 0;
  for (const raw of pages) {
    const p = raw as Record<string, unknown>;
    if (p.status !== "changed") continue; // "new" (first-seen baseline) has no diff and isn't news
    if (p.isMeaningful === false) continue; // Firecrawl's own judge already ruled this out
    const pageUrl = (p.url ?? "") as string;
    const changedText = (p.diff ?? "") as string;
    if (!pageUrl || !changedText) continue;

    const out = (await env.AI.run(NEWS_MODEL, {
      messages: [
        {
          role: "system",
          content:
            'You extract university/scholarship news from a webpage change, for an Indian student applying to MSc CS/AI/Software Engineering programs for Sept 2027 entry. Decide if the change is genuinely newsworthy (a new scholarship, deadline, fee change, or intake change) rather than incidental page edits. Reply with ONLY a JSON object: {"newsworthy": true|false, "university": "...", "country": "...", "headline": "...", "summary": "one or two sentences", "kind": "deadline"|"scholarship"|"fee_change"|"intake"|"update"}',
        },
        { role: "user", content: `URL: ${pageUrl}\n\nChanged content:\n${changedText.slice(0, 4000)}` },
      ],
      max_tokens: 2000, reasoning: { effort: "low" },
    } as never)) as { response?: string; choices?: { message: { content: string } }[] };
    const text = (out.response ?? out.choices?.[0]?.message?.content ?? "").trim();
    const start = text.indexOf("{");
    const end = text.lastIndexOf("}");
    let parsed: { newsworthy?: boolean; university?: string; country?: string; headline?: string; summary?: string; kind?: string } = {};
    try {
      parsed = JSON.parse(start >= 0 && end > start ? text.slice(start, end + 1) : text);
    } catch {
      continue;
    }
    if (!parsed.newsworthy || !parsed.headline) continue;

    const id = await sha1(pageUrl + parsed.headline);
    const exists = await env.DB.prepare("SELECT 1 FROM uni_news WHERE id = ?").bind(id).first();
    if (exists) continue;
    await env.DB.prepare(
      "INSERT INTO uni_news (id, university, country, source_url, headline, summary, kind) VALUES (?, ?, ?, ?, ?, ?, ?)",
    ).bind(id, parsed.university ?? "Unknown", parsed.country ?? "Unknown", pageUrl, parsed.headline, parsed.summary ?? null, parsed.kind ?? "update").run();
    added++;
  }
  return json({ added, pagesSeen: pages.length });
}
