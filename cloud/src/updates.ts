// In-app updates: the laptop uploads a signed APK, the phone checks for it and installs it.
// APKs live in Workers KV (free, values up to 25 MB); only the newest two builds are kept.
import type { Env } from "./index.ts";

const MAX_APK_BYTES = 25 * 1024 * 1024;

interface Release { versionCode: number; versionName: string; notes: string; size: number; sha256: string; publishedAt: string }

const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { "content-type": "application/json" } });

/** Laptop → server: the APK is the request body; version and notes come in headers. */
export async function publishRelease(req: Request, env: Env): Promise<Response> {
  const versionCode = Number(req.headers.get("x-version-code"));
  const versionName = req.headers.get("x-version-name") ?? String(versionCode);
  const notes = decodeURIComponent(req.headers.get("x-notes") ?? "");
  if (!Number.isInteger(versionCode) || versionCode < 1) return json({ error: "x-version-code missing" }, 400);
  const apk = await req.arrayBuffer();
  if (apk.byteLength < 1000 || apk.byteLength > MAX_APK_BYTES) return json({ error: `APK must be under 25 MB (got ${apk.byteLength})` }, 400);
  // APKs are zip files: a quick check that this is one.
  const magic = new Uint8Array(apk, 0, 2);
  if (magic[0] !== 0x50 || magic[1] !== 0x4b) return json({ error: "not an APK" }, 400);

  const previous = await env.APPS.get<Release>("latest", "json");
  if (previous && versionCode <= previous.versionCode) {
    return json({ error: `version ${versionCode} is not newer than ${previous.versionCode}` }, 409);
  }
  const digest = await crypto.subtle.digest("SHA-256", apk);
  const sha256 = [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
  const release: Release = { versionCode, versionName, notes, size: apk.byteLength, sha256, publishedAt: new Date().toISOString() };
  await env.APPS.put(`apk:${versionCode}`, apk);
  await env.APPS.put("latest", JSON.stringify(release));
  // Keep the previous build (a quick way back) and drop anything older.
  const keys = await env.APPS.list({ prefix: "apk:" });
  const old = keys.keys.map((k) => Number(k.name.slice(4))).filter((c) => c < (previous?.versionCode ?? 0));
  await Promise.all(old.map((c) => env.APPS.delete(`apk:${c}`)));
  return json(release);
}

export async function latestRelease(env: Env): Promise<Response> {
  const r = await env.APPS.get<Release>("latest", "json");
  return r ? json(r) : json({ error: "no release yet" }, 404);
}

export async function downloadApk(versionCode: number, env: Env): Promise<Response> {
  const apk = await env.APPS.get(`apk:${versionCode}`, "stream");
  if (!apk) return json({ error: "no such build" }, 404);
  return new Response(apk, {
    headers: {
      "content-type": "application/vnd.android.package-archive",
      "content-disposition": `attachment; filename="mind-core-${versionCode}.apk"`,
    },
  });
}
