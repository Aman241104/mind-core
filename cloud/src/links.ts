// URL normalization and shelf rules. Must stay identical to brain/src/mindcore/{whatsapp,triage}.py;
// both are tested against shared/vectors.json.

// Query params that only track the sharer; dropping them makes duplicates match.
const TRACKING = /^(utm_.*|igsh|igshid|igsi|stkn|si|fbclid|gclid|ref|ref_src|s|t|_vercel_share|img_index|feature|org)$/;

export function normalizeUrl(raw: string): string {
  raw = raw.replace(/[.,;:!?*_]+$/, "");
  const u = new URL(raw);
  let host = u.hostname.toLowerCase().replace(/^www\./, "").replace(/^m\./, "");
  let path = u.pathname;
  let query: string;
  if (host === "instagram.com" || host === "instagr.am") {
    // /reel/<code>/, /p/<code>/ and /<user>/reel/<code>/ are the same post
    const m = path.match(/\/(reel|reels|p|tv)\/([\w-]+)/);
    if (m) path = `/${m[1] === "p" ? "p" : "reel"}/${m[2]}/`;
  }
  if (host === "youtu.be") {
    host = "youtube.com";
    query = new URLSearchParams({ v: u.pathname.replace(/^\/+|\/+$/g, "") }).toString();
    path = "/watch";
  } else {
    const kept = [...u.searchParams].filter(([k]) => !TRACKING.test(k));
    query = new URLSearchParams(kept).toString();
  }
  if (path !== "" && path !== "/") path = path.replace(/\/+$/, "") + (host === "instagram.com" ? "/" : "");
  return `https://${host}${path || "/"}${query ? "?" + query : ""}`;
}

export function kindHint(url: string): string {
  const host = new URL(url).hostname;
  if (host === "instagram.com") return url.includes("/reel/") ? "reel" : "post";
  if (host === "youtube.com") return "reel";
  if (host === "github.com") return "github";
  if (/^(chatgpt\.com|claude\.ai|gemini\.google\.com|share\.gemini\.google|perplexity\.ai)$/.test(host) && url.includes("/share"))
    return "chat_share";
  return "page";
}

// GitHub owners that are you / your team: their repos are your own work, not finds.
const OWN_GITHUB = new Set(["aman241104", "mehtatechteam"]);
// Dropped entirely on import: not shown anywhere, not even on the Work shelf.
const SKIP = /astro|love.?problem|lovebackexpert|marriage|addword|adword/i;
const LEARNING_HOSTS = new Set([
  "instagram.com", "youtube.com", "x.com", "twitter.com", "linkedin.com", "reddit.com",
  "medium.com", "dev.to", "huggingface.co", "arxiv.org", "coursera.org", "udemy.com",
  "roadmap.sh", "chatgpt.com", "claude.ai", "gemini.google.com", "share.gemini.google",
  "perplexity.ai", "t.me",
]);
const WORK_HOSTS = new Set(["meet.google.com", "docs.google.com", "drive.google.com", "sheets.google.com", "calendar.google.com"]);
// Deploy previews are almost always your own client builds.
const WORK_HOST_PATTERNS = [/\.vercel\.app$/, /\.netlify\.app$/, /\.pages\.dev$/, /\.myshopify\.com$/];
const WORK_WORDS = /\b(client|invoice|quotation|seo|meta title|alt tags?|live site|deploy|demo|walkthrough|payment|proposal)\b/i;
const LEARNING_WORDS = /\b(learn|course|cert|roadmap|tutorial|repo|tool|try this|interview|dsa|free)\b/i;

export type Shelf = "learning" | "work" | "skip" | "unsure";
export interface Triage { shelf: Shelf; reason: string; mine: boolean }

export function triage(url: string, note = "", title = ""): Triage {
  const u = new URL(url);
  const host = u.hostname;
  const context = `${title} ${note}`;
  if (SKIP.test(host) || SKIP.test(title)) return { shelf: "skip", reason: "on the skip list", mine: false };
  if (host === "github.com") {
    const owner = u.pathname.replace(/^\/+/, "").split("/")[0].toLowerCase();
    if (OWN_GITHUB.has(owner)) return { shelf: "work", reason: `your own repo (${owner})`, mine: true };
    return { shelf: "learning", reason: "someone else's GitHub repo", mine: false };
  }
  if (WORK_HOSTS.has(host)) return { shelf: "work", reason: `${host} is a work tool`, mine: false };
  if (WORK_HOST_PATTERNS.some((p) => p.test(host))) return { shelf: "work", reason: "deploy preview of a site you built", mine: true };
  const work = context.match(WORK_WORDS);
  if (work) return { shelf: "work", reason: `work words in note: '${work[0]}'`, mine: false };
  if (LEARNING_HOSTS.has(host)) return { shelf: "learning", reason: `${host} is where you save finds`, mine: false };
  const learn = context.match(LEARNING_WORDS);
  if (learn) return { shelf: "learning", reason: `learning words in note: '${learn[0]}'`, mine: false };
  return { shelf: "unsure", reason: "unknown site, no clues in the note", mine: false };
}

export async function shortHash(text: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-1", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("").slice(0, 16);
}
