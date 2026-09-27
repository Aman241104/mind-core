// Voice notes: recorded on the phone, stored on your Cloudinary, transcribed here by Whisper (free tier).
import type { Env } from "./index.ts";
import { findDeadline, setDeadline } from "./deadlines.ts";

const WHISPER = "@cf/openai/whisper-large-v3-turbo"; // tested: m4a ok, ~2 s for 12 s of audio, auto language
const MAX_AUDIO_BYTES = 4 * 1024 * 1024; // ~15 min at the phone's 32 kbps; far more than a note needs

/** Only recordings in your own Cloudinary account, so the server never fetches arbitrary URLs. */
export function isOwnAudio(env: Env, url: string): boolean {
  const cloud = env.CLOUDINARY_URL?.match(/@(.+)$/)?.[1];
  return !!cloud && url.startsWith(`https://res.cloudinary.com/${cloud}/video/upload/`);
}

export async function transcribe(env: Env, url: string): Promise<string> {
  const resp = await fetch(url);
  if (!resp.ok) throw new Error(`couldn't fetch the recording (${resp.status})`);
  const bytes = new Uint8Array(await resp.arrayBuffer());
  if (bytes.byteLength > MAX_AUDIO_BYTES) throw new Error("recording is too long");
  // base64 in chunks: String.fromCharCode(...bigArray) overflows the call stack.
  let bin = "";
  for (let i = 0; i < bytes.length; i += 0x8000) bin += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  const out = (await env.AI.run(WHISPER, { audio: btoa(bin) } as never)) as { text?: string };
  return (out.text ?? "").trim();
}

/** "🎙 27 Sep: …" lines are appended to the item's note, so several voice notes can pile up. */
export async function addItemVoice(id: string, req: Request, env: Env): Promise<Response> {
  const body = (await req.json()) as { voice_url?: string };
  if (!body.voice_url || !isOwnAudio(env, body.voice_url)) return Response.json({ error: "voice_url must be your Cloudinary audio" }, { status: 400 });
  const text = await transcribe(env, body.voice_url);
  if (!text) return Response.json({ error: "couldn't hear anything in that recording" }, { status: 422 });
  const day = new Date().toLocaleDateString("en-IN", { day: "numeric", month: "short", timeZone: "Asia/Kolkata" });
  await env.DB.prepare(
    `UPDATE items SET user_note = trim(COALESCE(user_note, '') || char(10) || ?), updated_at = datetime('now') WHERE id = ?`,
  ).bind(`🎙 ${day}: ${text}`, id).run();
  // "…the deadline is 5 October" in a voice note sets the item's deadline.
  const deadline = await findDeadline(env, text).catch(() => null);
  if (deadline) await setDeadline(env, id, deadline, "voice");
  return Response.json({ ok: true, transcript: text, deadline });
}
