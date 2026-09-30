// mind-core API on Cloudflare Workers: saves in, items out, jobs for the laptop brain, hybrid search.
import { kindHint, normalizeUrl, shortHash, triage } from "./links.ts";
import { chatLinks } from "./whatsapp.ts";
import { downloadApk, latestRelease, publishRelease } from "./updates.ts";
import { addItemVoice, isOwnAudio, transcribe } from "./voice.ts";
import { backfillDeadlines, calendar, putDeadline, researchDeadline, upcoming } from "./deadlines.ts";
import { graph, linkSimilar } from "./graph.ts";
import { resurface } from "./resurface.ts";
import { dueCards, makeCards, reviewCard } from "./flashcards.ts";
import { createBoard, deleteBoard, getBoard, listBoards, saveBoard, suggestCards } from "./boards.ts";
import { createNote, deleteNote, getNote, listNotes, noteFromVoice, brainstorm, openTasks, purgeTrash, setTask, updateNote } from "./notes.ts";
import { ask, claimResearch, createResearch, finishResearch, getResearch, indexSaves, reindex } from "./ask.ts";
import { getTask, listAttempts, listResources, listTasks, progress, seedContent, submitAttempt } from "./ielts.ts";
import { ingestNews, ingestWebhook, listNews, markSeen } from "./news.ts";
import { listPlanActions, listPlanMarket, listPlanScholarships, listPlanUniversities, seedPlan, toggleWishlist } from "./plan.ts";

export interface Env {
  DB: D1Database;
  VEC: VectorizeIndex;
  VEC_CHUNKS: VectorizeIndex; // saved text (transcripts, captions, screen text) for Ask
  APPS: KVNamespace; // app builds for in-app updates
  AI: Ai;
  API_TOKEN: string; // the phone
  BRAIN_TOKEN: string; // the laptop
  CLOUDINARY_URL?: string; // cloudinary://key:secret@cloud, for screenshot uploads
  NEWS_WEBHOOK_KEY: string; // shared secret in the Firecrawl monitor webhook URL's ?key= param
}

const EMBED_MODEL = "@cf/baai/bge-m3"; // multilingual (Hindi/Hinglish), 1024 dims
const LEASE_MINUTES = 15;
const BRAIN_ONLINE_SECONDS = 120;

type Json = Record<string, unknown>;
const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });
const bad = (message: string, status = 400) => json({ error: message }, status);

function authorized(req: Request, env: Env, who: "phone" | "brain"): boolean {
  const token = req.headers.get("authorization")?.replace(/^Bearer /, "") ?? "";
  // The brain may also use phone endpoints (e.g. to import the WhatsApp backlog).
  return who === "phone" ? token === env.API_TOKEN || token === env.BRAIN_TOKEN : token === env.BRAIN_TOKEN;
}

export default {
  async fetch(req: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(req.url);
    const path = url.pathname;
    try {
      if (path === "/v1/health") return json({ ok: true });
      // Called directly by Firecrawl's servers, not the phone or the laptop — gated by its own ?key= check.
      if (path === "/v1/news/webhook" && req.method === "POST") return await ingestWebhook(req, env);
      if (path.startsWith("/v1/brain/")) {
        if (!authorized(req, env, "brain")) return bad("unauthorized", 401);
        if (path === "/v1/brain/heartbeat" && req.method === "POST") return await heartbeat(req, env);
        if (path === "/v1/brain/claim" && req.method === "POST") return await claim(req, env);
        if (path === "/v1/brain/complete" && req.method === "POST") return await complete(req, env);
        if (path === "/v1/brain/fail" && req.method === "POST") return await fail(req, env);
        if (path === "/v1/brain/reindex" && req.method === "POST") return await reindex(env);
        if (path === "/v1/brain/link-similar" && req.method === "POST") return json(await linkSimilar(env, 20));
        if (path === "/v1/brain/app" && req.method === "POST") return await publishRelease(req, env);
        if (path === "/v1/brain/deadlines/backfill" && req.method === "POST") return await backfillDeadlines(env);
        if (path === "/v1/brain/research/claim" && req.method === "POST") return await claimResearch(env);
        if (path === "/v1/brain/ielts/seed" && req.method === "POST") return await seedContent(req, env);
        if (path === "/v1/brain/news/ingest" && req.method === "POST") return await ingestNews(req, env);
        if (path === "/v1/brain/plan/seed" && req.method === "POST") return await seedPlan(req, env);
        const done = path.match(/^\/v1\/brain\/research\/(\d+)$/);
        if (done && req.method === "POST") return await finishResearch(Number(done[1]), req, env);
        const fix = path.match(/^\/v1\/brain\/items\/([0-9a-f]{16})$/);
        if (fix && req.method === "POST") return await correctItem(fix[1], req, env);
        return bad("not found", 404);
      }
      if (!authorized(req, env, "phone")) return bad("unauthorized", 401);
      if (path === "/v1/saves" && req.method === "POST") return await addSaves(req, env);
      if (path === "/v1/saves" && req.method === "GET") return await listSaves(url, env);
      if (path === "/v1/import/whatsapp" && req.method === "POST") return await importWhatsapp(req, env);
      if (path === "/v1/uploads/sign" && req.method === "POST") return await signUpload(env);
      if (path === "/v1/items" && req.method === "GET") return await listItems(url, env);
      const itemMatch = path.match(/^\/v1\/items\/([0-9a-f]{16})$/);
      if (itemMatch && req.method === "GET") return await getItem(itemMatch[1], env);
      if (itemMatch && req.method === "PATCH") return await patchItem(itemMatch[1], req, env);
      if (path === "/v1/notes" && req.method === "GET") return await listNotes(url, env);
      if (path === "/v1/notes" && req.method === "POST") return await createNote(req, env, ctx);
      if (path === "/v1/tasks" && req.method === "GET") return await openTasks(env);
      const task = path.match(/^\/v1\/notes\/([0-9a-f]{16})\/task$/);
      if (task && req.method === "POST") return await setTask(task[1], req, env, ctx);
      const bs = path.match(/^\/v1\/notes\/([0-9a-f]{16})\/brainstorm$/);
      if (bs && req.method === "POST") return await brainstorm(bs[1], req, env);
      if (path === "/v1/notes/voice" && req.method === "POST") return await noteFromVoice(req, env, ctx);
      const note = path.match(/^\/v1\/notes\/([0-9a-f]{16})$/);
      if (note && req.method === "GET") return await getNote(note[1], env);
      if (note && req.method === "PATCH") return await updateNote(note[1], req, env, ctx);
      if (note && req.method === "DELETE") return await deleteNote(note[1], url, env);
      if (path === "/v1/graph" && req.method === "GET") return await graph(env);
      if (path === "/v1/resurface" && req.method === "GET") return await resurface(env);
      if (path === "/v1/flashcards" && req.method === "POST") return await makeCards(req, env);
      if (path === "/v1/flashcards/due" && req.method === "GET") return await dueCards(env);
      const card = path.match(/^\/v1\/flashcards\/([0-9a-f]{16})\/review$/);
      if (card && req.method === "POST") return await reviewCard(card[1], req, env);
      if (path === "/v1/boards" && req.method === "GET") return await listBoards(env);
      if (path === "/v1/boards" && req.method === "POST") return await createBoard(req, env);
      const board = path.match(/^\/v1\/boards\/([0-9a-f]{16})(\/suggest)?$/);
      if (board && board[2] && req.method === "POST") return await suggestCards(board[1], req, env);
      if (board && !board[2] && req.method === "GET") return await getBoard(board[1], env);
      if (board && !board[2] && req.method === "PUT") return await saveBoard(board[1], req, env);
      if (board && !board[2] && req.method === "DELETE") return await deleteBoard(board[1], env);
      if (path === "/v1/calendar" && req.method === "GET") return await calendar(url, env);
      if (path === "/v1/upcoming" && req.method === "GET") return await upcoming(env);
      if (path === "/v1/ielts/resources" && req.method === "GET") return await listResources(url, env);
      if (path === "/v1/ielts/tasks" && req.method === "GET") return await listTasks(url, env);
      if (path === "/v1/ielts/attempts" && req.method === "GET") return await listAttempts(url, env);
      if (path === "/v1/ielts/attempts" && req.method === "POST") return await submitAttempt(req, env);
      if (path === "/v1/ielts/progress" && req.method === "GET") return await progress(env);
      const ieltsTask = path.match(/^\/v1\/ielts\/tasks\/([0-9a-f-]+)$/);
      if (ieltsTask && req.method === "GET") return await getTask(ieltsTask[1], env);
      if (path === "/v1/news" && req.method === "GET") return await listNews(url, env);
      if (path === "/v1/plan/actions" && req.method === "GET") return await listPlanActions(env);
      if (path === "/v1/plan/universities" && req.method === "GET") return await listPlanUniversities(env);
      if (path === "/v1/plan/market" && req.method === "GET") return await listPlanMarket(env);
      if (path === "/v1/plan/scholarships" && req.method === "GET") return await listPlanScholarships(env);
      const wishlist = path.match(/^\/v1\/plan\/universities\/([0-9a-f-]+)\/wishlist$/);
      if (wishlist && req.method === "POST") return await toggleWishlist(wishlist[1], env);
      const newsSeen = path.match(/^\/v1\/news\/([0-9a-f]+)\/seen$/);
      if (newsSeen && req.method === "POST") return await markSeen(newsSeen[1], env);
      const dl = path.match(/^\/v1\/items\/([0-9a-f]{16})\/(deadline|find-deadline)$/);
      if (dl && dl[2] === "deadline" && req.method === "PUT") return await putDeadline(dl[1], req, env);
      if (dl && dl[2] === "find-deadline" && req.method === "POST") return await researchDeadline(dl[1], env);
      const voice = path.match(/^\/v1\/items\/([0-9a-f]{16})\/voice$/);
      if (voice && req.method === "POST") return await addItemVoice(voice[1], req, env);
      if (path === "/v1/search" && req.method === "POST") return await search(req, env);
      if (path === "/v1/ask" && req.method === "POST") return await ask(req, env, (q, limit) => hybridItems(env, q, {}, limit));
      if (path === "/v1/research" && req.method === "POST") return await createResearch(req, env);
      if (path === "/v1/app/latest" && req.method === "GET") return await latestRelease(env);
      const apk = path.match(/^\/v1\/app\/apk\/(\d+)$/);
      if (apk && req.method === "GET") return await downloadApk(Number(apk[1]), env);
      const research = path.match(/^\/v1\/research\/(\d+)$/);
      if (research && req.method === "GET") return await getResearch(Number(research[1]), env);
      if (path === "/v1/status" && req.method === "GET") return await status(env);
      return bad("not found", 404);
    } catch (e) {
      return bad(e instanceof Error ? e.message : String(e), 500);
    }
  },

  // Every 5 minutes: hand back jobs whose worker vanished mid-lease.
  async scheduled(_controller: ScheduledController, env: Env): Promise<void> {
    await env.DB.prepare(
      `UPDATE jobs SET status = 'pending', lease_until = NULL, updated_at = datetime('now')
       WHERE status = 'leased' AND lease_until < datetime('now')`,
    ).run();
    await linkSimilar(env); // graph view: link new items to similar ones
    await purgeTrash(env);
  },
} satisfies ExportedHandler<Env>;

// ---------- saves ----------

interface SaveIn {
  url?: string; image_url?: string; text?: string; note?: string; title?: string; source?: string; saved_at?: string;
  voice_url?: string; // a voice note recorded with this save
  file_url?: string; // a PDF (book, paper) uploaded to your Cloudinary
}
type Counts = { added: number; duplicate: number; skipped: number; queued: number; invalid: number };

async function addSaves(req: Request, env: Env): Promise<Response> {
  const body = (await req.json()) as { saves?: SaveIn[] };
  const saves = body.saves ?? [];
  if (!Array.isArray(saves) || saves.length === 0 || saves.length > 500) return bad("send 1-500 saves");
  return json(await ingest(env, saves));
}

/** A link, a screenshot (already on Cloudinary) or a plain note. Duplicates are ignored. */
async function ingest(env: Env, saves: SaveIn[]): Promise<Counts> {
  const counts: Counts = { added: 0, duplicate: 0, skipped: 0, queued: 0, invalid: 0 };
  const statements: D1PreparedStatement[] = [];
  const seen = new Set<string>();
  for (const s of saves) {
    if (s.voice_url) {
      if (!isOwnAudio(env, s.voice_url)) { counts.invalid++; continue; }
      const heard = await transcribe(env, s.voice_url).catch(() => "");
      if (heard) s.note = [s.note, `Voice note: ${heard}`].filter(Boolean).join("\n");
      if (!s.url && !s.image_url && !s.text) s.text = heard || undefined;
    }
    let row: { id: string; url: string | null; host: string | null; kind: string; shelf: string; mine: boolean; note: string | null };
    if (s.url) {
      let norm: string;
      try { norm = normalizeUrl(s.url); } catch { counts.invalid++; continue; }
      const t = triage(norm, s.note ?? "", s.title ?? "");
      if (t.shelf === "skip") { counts.skipped++; continue; }
      row = { id: await shortHash(norm), url: norm, host: new URL(norm).hostname, kind: kindHint(norm),
        shelf: t.shelf, mine: t.mine, note: s.note ?? null };
    } else if (s.image_url) {
      // Only images in your own Cloudinary account, so nobody can make the laptop fetch arbitrary URLs.
      const cloud = cloudinary(env)?.cloud;
      if (!cloud || !s.image_url.startsWith(`https://res.cloudinary.com/${cloud}/`)) { counts.invalid++; continue; }
      row = { id: await shortHash(s.image_url), url: s.image_url, host: "res.cloudinary.com", kind: "image",
        shelf: "unsure", mine: false, note: s.note ?? null };
    } else if (s.file_url) {
      const cloud = cloudinary(env)?.cloud;
      if (!cloud || !s.file_url.startsWith(`https://res.cloudinary.com/${cloud}/raw/upload/`)) { counts.invalid++; continue; }
      row = { id: await shortHash(s.file_url), url: s.file_url, host: "res.cloudinary.com", kind: "pdf",
        shelf: "learning", mine: false, note: s.note ?? null };
    } else if (s.text?.trim()) {
      row = { id: await shortHash("text:" + s.text.trim()), url: null, host: null, kind: "text",
        shelf: "unsure", mine: false, note: s.text.trim().slice(0, 4000) };
    } else { counts.invalid++; continue; }

    if (seen.has(row.id) || (await env.DB.prepare("SELECT 1 FROM saves WHERE id = ?").bind(row.id).first())) {
      counts.duplicate++;
      continue;
    }
    seen.add(row.id);
    counts.added++;
    // Work links are just filed; learning and unsure ones get processed by the brain.
    const process = row.shelf !== "work";
    statements.push(
      env.DB.prepare(
        `INSERT INTO saves (id, url, raw_url, host, kind_hint, shelf, mine, title, note, source, saved_at, status)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      ).bind(row.id, row.url, s.url ?? s.image_url ?? s.file_url ?? null, row.host, row.kind, row.shelf, row.mine ? 1 : 0,
        s.title ?? null, row.note, s.source ?? "share", s.saved_at ?? new Date().toISOString(), process ? "queued" : "done"),
      ...(s.voice_url ? [env.DB.prepare("UPDATE saves SET voice_url = ? WHERE id = ?").bind(s.voice_url, row.id)] : []),
    );
    if (process) {
      counts.queued++;
      statements.push(env.DB.prepare("INSERT INTO jobs (save_id, type) VALUES (?, ?)").bind(row.id, row.kind));
    }
  }
  if (statements.length) await env.DB.batch(statements);
  return counts;
}

/** A WhatsApp "Export chat" .txt, sent by the phone. Whole messages that are skip-lists are dropped. */
async function importWhatsapp(req: Request, env: Env): Promise<Response> {
  const text = await req.text();
  if (!text || text.length > 5_000_000) return bad("send the chat .txt (max 5 MB)");
  const links = chatLinks(text);
  const skipMsgs = new Set(links.filter((l) => {
    try { return triage(normalizeUrl(l.url), l.note, l.title ?? "").shelf === "skip"; } catch { return false; }
  }).map((l) => l.msgIndex));
  const saves: SaveIn[] = links.filter((l) => !skipMsgs.has(l.msgIndex))
    .map((l) => ({ url: l.url, note: l.note, title: l.title ?? undefined, source: "whatsapp", saved_at: l.savedAt }));
  const total: Counts = { added: 0, duplicate: 0, skipped: skipMsgs.size ? links.length - saves.length : 0, queued: 0, invalid: 0 };
  for (let i = 0; i < saves.length; i += 200) {
    const c = await ingest(env, saves.slice(i, i + 200));
    for (const k of Object.keys(total) as (keyof Counts)[]) total[k] += c[k];
  }
  return json({ found: links.length, ...total });
}

// ---------- Cloudinary (screenshots) ----------

function cloudinary(env: Env): { cloud: string; key: string; secret: string } | null {
  const m = env.CLOUDINARY_URL?.match(/^cloudinary:\/\/([^:]+):([^@]+)@(.+)$/);
  return m ? { key: m[1], secret: m[2], cloud: m[3] } : null;
}

/** Signed upload params: the phone uploads straight to Cloudinary; the API secret stays here. */
async function signUpload(env: Env): Promise<Response> {
  const c = cloudinary(env);
  if (!c) return bad("screenshots aren't set up yet (CLOUDINARY_URL missing on the server)", 503);
  const timestamp = Math.floor(Date.now() / 1000);
  const folder = "mind-core";
  const toSign = `folder=${folder}&timestamp=${timestamp}${c.secret}`;
  const digest = await crypto.subtle.digest("SHA-1", new TextEncoder().encode(toSign));
  const signature = [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
  return json({ cloud_name: c.cloud, api_key: c.key, timestamp, folder, signature,
    upload_url: `https://api.cloudinary.com/v1_1/${c.cloud}/image/upload`,
    // Cloudinary files audio under "video".
    audio_upload_url: `https://api.cloudinary.com/v1_1/${c.cloud}/video/upload`,
    file_upload_url: `https://api.cloudinary.com/v1_1/${c.cloud}/raw/upload` });
}

async function listSaves(url: URL, env: Env): Promise<Response> {
  const shelf = url.searchParams.get("shelf");
  const st = url.searchParams.get("status");
  const limit = Math.min(Number(url.searchParams.get("limit") ?? 100), 500);
  const where: string[] = [];
  const args: unknown[] = [];
  if (shelf) { where.push("shelf = ?"); args.push(shelf); }
  if (st) { where.push("status = ?"); args.push(st); }
  const sql = `SELECT id, url, host, kind_hint, shelf, mine, title, note, source, saved_at, status, error, creator, promo
               FROM saves ${where.length ? "WHERE " + where.join(" AND ") : ""} ORDER BY saved_at DESC LIMIT ?`;
  const rows = await env.DB.prepare(sql).bind(...args, limit).all();
  return json(rows.results);
}

// ---------- items ----------

async function listItems(url: URL, env: Env): Promise<Response> {
  const where: string[] = [];
  const args: unknown[] = [];
  for (const f of ["kind", "shelf", "trust", "status"]) {
    const v = url.searchParams.get(f);
    if (v) { where.push(`${f} = ?`); args.push(v); }
  }
  const limit = Math.min(Number(url.searchParams.get("limit") ?? 100), 500);
  const rows = await env.DB.prepare(
    `SELECT i.*, (SELECT COUNT(*) FROM item_sources s WHERE s.item_id = i.id) AS source_count
     FROM items i ${where.length ? "WHERE " + where.join(" AND ") : ""} ORDER BY updated_at DESC LIMIT ?`,
  ).bind(...args, limit).all();
  return json(rows.results.map(parseItem));
}

function parseItem(row: Record<string, unknown>) {
  return { ...row, verification: row.verification ? JSON.parse(String(row.verification)) : null };
}

async function getItem(id: string, env: Env): Promise<Response> {
  const item = await env.DB.prepare("SELECT * FROM items WHERE id = ?").bind(id).first();
  if (!item) return bad("no such item", 404);
  const sources = await env.DB.prepare(
    `SELECT s.id, s.url, s.kind_hint, s.voice_url, s.creator, s.caption, s.saved_at, s.promo, x.claims, x.needs_frames
     FROM item_sources x JOIN saves s ON s.id = x.save_id WHERE x.item_id = ? ORDER BY s.saved_at DESC`,
  ).bind(id).all();
  const voices = await env.DB.prepare(
    "SELECT url, transcript, created_at FROM voice_notes WHERE item_id = ? ORDER BY created_at DESC",
  ).bind(id).all();
  // One row per related item (it can be linked both ways and by more than one reason); saved-together first.
  const related = await env.DB.prepare(
    `SELECT min(type) AS type, id, name, kind, trust FROM (
       SELECT r.type, i.id, i.name, i.kind, i.trust FROM relations r JOIN items i ON i.id = r.b WHERE r.a = ?1 AND r.type != 'checked'
       UNION SELECT r.type, i.id, i.name, i.kind, i.trust FROM relations r JOIN items i ON i.id = r.a WHERE r.b = ?1 AND r.type != 'checked')
     WHERE id != ?1 GROUP BY id ORDER BY type LIMIT 12`,
  ).bind(id).all();
  // Your notes that link here ([[name]] or found related by meaning).
  const notes = await env.DB.prepare(
    `SELECT n.id, n.title, n.kind, min(l.type) AS type FROM note_links l JOIN notes n ON n.id = l.src_id
     WHERE l.dst_id = ? AND l.dst_type = 'item' AND n.deleted_at IS NULL GROUP BY n.id ORDER BY type, n.updated_at DESC LIMIT 12`,
  ).bind(id).all();
  return json({
    ...parseItem(item),
    // Saves with no web link (notes, voice, screenshots) report url=null so the app doesn't try to open them.
    sources: sources.results.map((s) => ({
      ...s, url: ["text", "pdf"].includes(String(s.kind_hint)) ? null : s.url, claims: s.claims ? JSON.parse(String(s.claims)) : [],
    })),
    related: related.results,
    voice_notes: voices.results,
    notes: notes.results,
  });
}

async function patchItem(id: string, req: Request, env: Env): Promise<Response> {
  const body = (await req.json()) as { status?: string; user_note?: string; favorite?: boolean };
  const allowed = new Set(["new", "want", "trying", "done", "skip"]);
  if (body.status && !allowed.has(body.status)) return bad("bad status");
  await env.DB.prepare(
    `UPDATE items SET status = COALESCE(?, status), user_note = COALESCE(?, user_note),
       favorite = COALESCE(?, favorite), updated_at = datetime('now')
     WHERE id = ?`,
  ).bind(body.status ?? null, body.user_note ?? null, body.favorite == null ? null : body.favorite ? 1 : 0, id).run();
  return getItem(id, env);
}

// ---------- brain ----------

async function heartbeat(req: Request, env: Env): Promise<Response> {
  const info = await req.text();
  await env.DB.prepare(
    `INSERT INTO brain (id, last_seen, info) VALUES ('laptop', datetime('now'), ?)
     ON CONFLICT(id) DO UPDATE SET last_seen = excluded.last_seen, info = excluded.info`,
  ).bind(info || null).run();
  return json({ ok: true });
}

async function claim(req: Request, env: Env): Promise<Response> {
  const body = (await req.json()) as { types?: string[]; limit?: number };
  const limit = Math.min(body.limit ?? 8, 25);
  const types = body.types?.length ? body.types : ["reel", "post", "page", "github", "chat_share", "image", "text", "video", "playlist", "pdf"];
  const placeholders = types.map(() => "?").join(",");
  // One statement, so two workers can't lease the same job.
  const leased = await env.DB.prepare(
    `UPDATE jobs SET status = 'leased', attempts = attempts + 1, updated_at = datetime('now'),
       lease_until = datetime('now', '+${LEASE_MINUTES} minutes')
     WHERE id IN (SELECT id FROM jobs WHERE runner = 'brain' AND status = 'pending' AND type IN (${placeholders})
                  ORDER BY id LIMIT ?)
     RETURNING id, save_id, type, attempts`,
  ).bind(...types, limit).all<{ id: number; save_id: string; type: string; attempts: number }>();
  if (!leased.results.length) return json([]);
  const ids = leased.results.map((j) => j.save_id);
  const saves = await env.DB.prepare(
    `SELECT id, url, kind_hint, shelf, title, note, saved_at FROM saves WHERE id IN (${ids.map(() => "?").join(",")})`,
  ).bind(...ids).all();
  const byId = new Map(saves.results.map((s) => [s.id, s]));
  await env.DB.prepare(
    `UPDATE saves SET status = 'processing' WHERE id IN (${ids.map(() => "?").join(",")})`,
  ).bind(...ids).run();
  return json(leased.results.map((j) => ({ ...j, save: byId.get(j.save_id) })));
}

interface ItemIn {
  kind: string; name: string; url?: string | null; one_line?: string; claims?: string[];
  needs_frames?: boolean; canonical_key?: string; trust?: string; verification?: Json;
  deadline?: string | null; // YYYY-MM-DD, from the post or your note/voice note
}
interface CompleteIn {
  job_id: number;
  save: {
    creator?: string; caption?: string; transcript?: string; screen_text?: string; language?: string; promo?: boolean; shelf?: string;
  };
  items: ItemIn[];
}

async function complete(req: Request, env: Env): Promise<Response> {
  const body = (await req.json()) as CompleteIn;
  const job = await env.DB.prepare("SELECT save_id FROM jobs WHERE id = ?").bind(body.job_id).first<{ save_id: string }>();
  if (!job) return bad("no such job", 404);
  const saveId = job.save_id;
  const s = body.save ?? {};
  // The brain decides "unsure" saves; only learning ones keep their items.
  const shelf = s.shelf === "work" || s.shelf === "learning" ? s.shelf : undefined;
  const statements: D1PreparedStatement[] = [
    env.DB.prepare(
      `UPDATE saves SET status = 'done', error = NULL, creator = ?, caption = ?, transcript = ?, screen_text = ?, language = ?,
         promo = ?, shelf = COALESCE(?, shelf) WHERE id = ?`,
    ).bind(s.creator ?? null, s.caption ?? null, s.transcript ?? null, s.screen_text?.slice(0, 20000) ?? null, s.language ?? null,
      s.promo == null ? null : s.promo ? 1 : 0, shelf ?? null, saveId),
    env.DB.prepare("UPDATE jobs SET status = 'done', error = NULL, updated_at = datetime('now') WHERE id = ?").bind(body.job_id),
  ];

  const items = shelf === "work" ? [] : body.items ?? [];
  const itemIds: string[] = [];
  const vectors: VectorizeVector[] = [];
  for (const it of items) {
    if (!it.name || !it.kind) continue;
    const key = it.canonical_key || `name:${it.kind}:${it.name.toLowerCase().replace(/\s+/g, " ").trim()}`;
    const id = await shortHash(key);
    itemIds.push(id);
    const claims = JSON.stringify(it.claims ?? []);
    statements.push(
      // A second reel about the same repo updates facts but never downgrades trust or loses your status.
      env.DB.prepare(
        `INSERT INTO items (id, canonical_key, kind, name, url, one_line, trust, verification)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)
         ON CONFLICT(id) DO UPDATE SET
           url = COALESCE(excluded.url, items.url),
           one_line = COALESCE(items.one_line, excluded.one_line),
           verification = COALESCE(excluded.verification, items.verification),
           trust = CASE WHEN excluded.trust = 'verified' THEN 'verified' ELSE items.trust END,
           updated_at = datetime('now')`,
      ).bind(id, key, it.kind, it.name, it.url ?? null, it.one_line ?? null, it.trust ?? "unconfirmed",
        it.verification ? JSON.stringify(it.verification) : null),
      env.DB.prepare(
        `INSERT INTO item_sources (item_id, save_id, claims, needs_frames) VALUES (?, ?, ?, ?)
         ON CONFLICT(item_id, save_id) DO UPDATE SET claims = excluded.claims, needs_frames = excluded.needs_frames`,
      ).bind(id, saveId, claims, it.needs_frames ? 1 : 0),
      ...(it.deadline && /^\d{4}-\d{2}-\d{2}$/.test(it.deadline)
        ? [env.DB.prepare(`UPDATE items SET deadline = ?, deadline_source = 'post' WHERE id = ? AND deadline IS NULL`).bind(it.deadline, id)]
        : []),
      env.DB.prepare("DELETE FROM items_fts WHERE item_id = ?").bind(id),
      env.DB.prepare("INSERT INTO items_fts (item_id, name, one_line, claims) VALUES (?, ?, ?, ?)")
        .bind(id, it.name, it.one_line ?? "", (it.claims ?? []).join(" ")),
    );
    vectors.push({
      id,
      values: [], // filled below in one embedding call
      metadata: { kind: it.kind, shelf: "learning", trust: it.trust ?? "unconfirmed" },
    });
  }
  // Items from the same save are related.
  for (let i = 0; i < itemIds.length; i++)
    for (let j = i + 1; j < itemIds.length; j++) {
      const [a, b] = [itemIds[i], itemIds[j]].sort();
      if (a !== b)
        statements.push(env.DB.prepare("INSERT OR IGNORE INTO relations (a, b, type) VALUES (?, ?, 'mentioned_together')").bind(a, b));
    }
  await env.DB.batch(statements);

  if (vectors.length) {
    const texts = items.filter((it) => it.name && it.kind)
      .map((it) => `${it.kind}: ${it.name}. ${it.one_line ?? ""} ${(it.claims ?? []).join(". ")}`);
    const out = (await env.AI.run(EMBED_MODEL, { text: texts })) as { data: number[][] };
    out.data.forEach((v, i) => (vectors[i].values = v));
    await env.VEC.upsert(vectors);
  }
  if (shelf !== "work") await indexSaves(env, [saveId]); // makes its text quotable by Ask
  return json({ ok: true, items: itemIds.length });
}

/** Explicit correction from the brain (e.g. a stricter re-check). Unlike complete(), this may lower trust. */
async function correctItem(id: string, req: Request, env: Env): Promise<Response> {
  const body = (await req.json()) as { trust?: string; verification?: Json };
  const allowed = new Set(["verified", "check", "unconfirmed", "dead", "hype"]);
  if (!body.trust || !allowed.has(body.trust)) return bad("bad trust");
  const r = await env.DB.prepare(
    `UPDATE items SET trust = ?, verification = COALESCE(?, verification), updated_at = updated_at WHERE id = ?`,
  ).bind(body.trust, body.verification ? JSON.stringify(body.verification) : null, id).run();
  if (!r.meta.changes) return bad("no such item", 404);
  // Keep the search filter in step with the new trust.
  const vec = await env.VEC.getByIds([id]);
  if (vec[0]?.values?.length) await env.VEC.upsert([{ ...vec[0], metadata: { ...vec[0].metadata, trust: body.trust } }]);
  return json({ ok: true });
}

async function fail(req: Request, env: Env): Promise<Response> {
  const body = (await req.json()) as { job_id: number; error: string; retry?: boolean };
  const job = await env.DB.prepare("SELECT save_id, attempts FROM jobs WHERE id = ?").bind(body.job_id)
    .first<{ save_id: string; attempts: number }>();
  if (!job) return bad("no such job", 404);
  const giveUp = !body.retry || job.attempts >= 3;
  await env.DB.batch([
    env.DB.prepare(
      `UPDATE jobs SET status = ?, error = ?, lease_until = NULL, updated_at = datetime('now') WHERE id = ?`,
    ).bind(giveUp ? "failed" : "pending", body.error.slice(0, 1000), body.job_id),
    env.DB.prepare("UPDATE saves SET status = ?, error = ? WHERE id = ?")
      .bind(giveUp ? "failed" : "queued", body.error.slice(0, 1000), job.save_id),
  ]);
  return json({ ok: true, gave_up: giveUp });
}

// ---------- search & status ----------

async function search(req: Request, env: Env): Promise<Response> {
  const body = (await req.json()) as { q: string; kind?: string; trust?: string; limit?: number };
  if (!body.q?.trim()) return bad("empty query");
  return json(await hybridItems(env, body.q, { kind: body.kind, trust: body.trust }, Math.min(body.limit ?? 10, 50)));
}

/** Meaning search (Vectorize) + keyword search (FTS5), merged with reciprocal rank fusion. */
async function hybridItems(env: Env, q: string, f: { kind?: string; trust?: string }, limit: number) {
  const filter: VectorizeVectorMetadataFilter = { shelf: "learning" };
  if (f.kind) filter.kind = f.kind;
  if (f.trust) filter.trust = f.trust;
  const emb = (await env.AI.run(EMBED_MODEL, { text: [q] })) as { data: number[][] };
  const [vec, fts] = await Promise.all([
    env.VEC.query(emb.data[0], { topK: 30, filter }),
    env.DB.prepare(
      `SELECT f.item_id FROM items_fts f JOIN items i ON i.id = f.item_id
       WHERE items_fts MATCH ? ${f.kind ? "AND i.kind = ?" : ""} ${f.trust ? "AND i.trust = ?" : ""}
       ORDER BY bm25(items_fts) LIMIT 30`,
    ).bind(ftsQuery(q), ...[f.kind, f.trust].filter(Boolean)).all<{ item_id: string }>(),
  ]);
  const score = new Map<string, number>();
  vec.matches.forEach((m, rank) => score.set(m.id, (score.get(m.id) ?? 0) + 1 / (60 + rank)));
  fts.results.forEach((r, rank) => score.set(r.item_id, (score.get(r.item_id) ?? 0) + 1 / (60 + rank)));
  const top = [...score.entries()].sort((a, b) => b[1] - a[1]).slice(0, limit).map(([id]) => id);
  if (!top.length) return [];
  const rows = await env.DB.prepare(`SELECT * FROM items WHERE id IN (${top.map(() => "?").join(",")})`).bind(...top).all();
  const byId = new Map(rows.results.map((r) => [r.id as string, parseItem(r)]));
  return top.map((id) => byId.get(id)).filter((x): x is ReturnType<typeof parseItem> => !!x);
}

/** Turn free text into a safe FTS5 query: each word as a prefix term, OR-ed. */
function ftsQuery(q: string): string {
  const words = q.toLowerCase().match(/[\p{L}\p{N}]+/gu) ?? [];
  return words.map((w) => `"${w}"*`).join(" OR ") || '""';
}

async function status(env: Env): Promise<Response> {
  const [brain, jobs, saves, items] = await Promise.all([
    env.DB.prepare(
      `SELECT last_seen, info, (strftime('%s','now') - strftime('%s', last_seen)) AS age FROM brain WHERE id = 'laptop'`,
    ).first<{ last_seen: string; info: string; age: number }>(),
    env.DB.prepare("SELECT status, COUNT(*) AS n FROM jobs GROUP BY status").all(),
    env.DB.prepare("SELECT shelf, COUNT(*) AS n FROM saves GROUP BY shelf").all(),
    env.DB.prepare("SELECT kind, COUNT(*) AS n FROM items GROUP BY kind").all(),
  ]);
  return json({
    laptop: { online: !!brain && brain.age < BRAIN_ONLINE_SECONDS, last_seen: brain?.last_seen ?? null },
    jobs: Object.fromEntries(jobs.results.map((r) => [r.status, r.n])),
    saves: Object.fromEntries(saves.results.map((r) => [r.shelf, r.n])),
    items: Object.fromEntries(items.results.map((r) => [r.kind, r.n])),
  });
}
