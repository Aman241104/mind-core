// WhatsApp chat export parsing, the TypeScript twin of brain/src/mindcore/whatsapp.py.
// Returns one entry per link with its note, list title and time. Tested against shared/vectors.json.

const INVISIBLE = /[‎‏‪‬⁨⁩﻿]/g;
const IOS_LINE = /^\[(\d{1,2}\/\d{1,2}\/\d{2,4}), (\d{1,2}:\d{2}(?::\d{2})?\s?[AaPp]\.?[Mm]\.?)\] ([^:]+): (.*)$/;
const ANDROID_LINE = /^(\d{1,2}\/\d{1,2}\/\d{2,4}), (\d{1,2}:\d{2}(?:\s?[AaPp]\.?[Mm]\.?)?) - ([^:]+): (.*)$/;
const URL_RE = /https?:\/\/[^\s<>"')\]]+/g;
const FORWARDED = "[Forwarded]";

export interface ChatLink { url: string; note: string; title: string | null; savedAt: string; msgIndex: number }

interface Msg { when: string; text: string }

function parseWhen(date: string, time: string, dayFirst: boolean): string {
  const [a, b, y] = date.split("/");
  const [month, day] = dayFirst ? [Number(b), Number(a)] : [Number(a), Number(b)];
  const year = y.length === 2 ? 2000 + Number(y) : Number(y);
  const t = time.replace(/[.\s]/g, "").toUpperCase();
  const m = t.match(/^(\d{1,2}):(\d{2})(?::(\d{2}))?(AM|PM)?$/);
  if (!m) throw new Error(`bad time ${time}`);
  let hour = Number(m[1]);
  if (m[4] === "PM" && hour !== 12) hour += 12;
  if (m[4] === "AM" && hour === 12) hour = 0;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${year}-${pad(month)}-${pad(day)}T${pad(hour)}:${m[2]}:${m[3] ?? "00"}`;
}

export function parseChat(raw: string): Msg[] {
  const lines = raw.replace(INVISIBLE, "").split(/\r?\n/);
  const pattern = lines.slice(0, 50).some((l) => IOS_LINE.test(l)) ? IOS_LINE : ANDROID_LINE;
  // A first number above 12 anywhere means dates are day/month; Android exports in India default to it.
  let dayFirst = pattern === ANDROID_LINE;
  for (const l of lines) {
    const m = l.match(pattern);
    if (!m) continue;
    const [a, b] = m[1].split("/").map(Number);
    if (a > 12) { dayFirst = true; break; }
    if (b > 12) { dayFirst = false; break; }
  }
  const msgs: Msg[] = [];
  for (const line of lines) {
    const m = line.match(pattern);
    if (!m) {
      if (msgs.length) msgs[msgs.length - 1].text += "\n" + line;
      continue;
    }
    let body = m[4];
    if (body.startsWith(FORWARDED)) body = body.slice(FORWARDED.length).trimStart();
    msgs.push({ when: parseWhen(m[1], m[2], dayFirst), text: body });
  }
  return msgs;
}

export function chatLinks(raw: string): ChatLink[] {
  const out: ChatLink[] = [];
  parseChat(raw).forEach((msg, i) => {
    if (!URL_RE.test(msg.text)) return;
    URL_RE.lastIndex = 0;
    const note = msg.text.replace(URL_RE, "").trim().slice(0, 500);
    let title: string | null = null;
    for (const line of msg.text.split("\n")) {
      const urls = line.match(URL_RE) ?? [];
      const stripped = line.trim().replace(/^[*_ \-:]+|[*_ \-:]+$/g, "");
      if (stripped && !urls.length) title = stripped.slice(0, 120);
      for (const url of urls) out.push({ url, note, title, savedAt: msg.when, msgIndex: i });
    }
  });
  return out;
}
