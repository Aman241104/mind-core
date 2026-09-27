"""Pull items (repos, tools, certs, courses, jobs, tips) out of reel transcripts + captions.

Engine for now: `claude -p` on the laptop (your Claude plan). Several saves go in one call
to spend less of the plan's limit.
"""

from __future__ import annotations

import json
import re
import subprocess
from dataclasses import dataclass

PROMPT = """You extract saveable items from short social-media videos about tech and careers.
For each source below (caption, speech transcript, and text read off the screen or page), list the concrete
things worth saving.

Rules:
- kind is one of: repo, tool, cert, course, job, tip, video, playlist, book, other.
- If the source itself is a YouTube video, a YouTube playlist, or a PDF book/document (the caption says so), the
  FIRST item is the resource itself: kind video / playlist / book, name = its real title, one_line = what you
  learn from it, claims = length (minutes / number of videos / pages), level, and key topics. Then list any
  repos/tools/courses it recommends as separate items.
- name: the real name. Transcripts are speech-to-text and mishear words ("cloud code" = Claude Code,
  "3.js" = Three.js, "hiding" = hiring). Fix obvious mishearings, but never invent a name that isn't
  said or shown. If the video only says "comment X and I'll DM the link", set name to what it IS
  (e.g. "codebase-to-animated-diagram skill") and needs_frames=true.
- url: only if it is stated in the caption, transcript or screen text. Otherwise null. Never guess URLs.
- claims: short factual claims the video makes about the item (numbers, "free", salary, stars).
  These get fact-checked later, so copy them faithfully, even if they look wrong.
- one_line: what it is, in plain words, max 15 words.
- screen text comes from OCR and is noisy; use it to find names/URLs the speaker didn't say.
- promo: true if the video is clearly an ad / "comment to get the link" bait.
- deadline: YYYY-MM-DD if the source or the owner's note/voice note gives a date to act by (last date to apply,
  register, submit, an offer ending, "remind me on ..."), else null. Resolve dates without a year to the first such
  date on or after the source's saved date. A deadline that has already passed is still a deadline.
- shelf: "learning" if it's something to learn/try/apply to, "work" if it's the owner's own client/business
  material (client websites, suppliers, competitor research for a client). Sources marked shelf_hint=learning
  are almost always learning; decide carefully for shelf_hint=unsure. Work sources get no items.

Return ONLY JSON: {"<source id>": {"shelf": "learning"|"work", "items": [{"kind","name","url","one_line",
"claims":[...], "needs_frames": bool, "deadline": "YYYY-MM-DD"|null}], "promo": bool, "language": "<lang>"}}

Sources:
"""


@dataclass
class Source:
    id: str
    caption: str
    transcript: str
    creator: str | None = None
    screen_text: str = ""
    shelf_hint: str = "learning"
    note: str = ""
    saved: str = ""


def build_prompt(sources: list[Source]) -> str:
    parts = [
        f"### {s.id}\nsaved: {s.saved[:10]}\nshelf_hint: {s.shelf_hint}\ncreator: {s.creator or '?'}\nyour note: {s.note[:600]}\n"
        f"caption: {s.caption[:1500]}\ntranscript: {s.transcript[:6000]}\nscreen text: {s.screen_text[:4000]}"
        for s in sources
    ]
    return PROMPT + "\n\n".join(parts)


def _json_from(text: str) -> dict:
    m = re.search(r"\{.*\}", text, re.S)
    if not m:
        raise ValueError(f"no JSON in model output: {text[:200]!r}")
    # strict=False: models often put raw line breaks inside long answer strings.
    return json.loads(m[0], strict=False)


# A lean headless call: no tools, MCP servers, skills, hooks or CLAUDE.md, just the prompt. This keeps each
# call ~500 tokens of overhead instead of ~55k, and nothing in your Claude setup can interfere with it.
# (--bare would be leaner still, but it only works with an API key, not your Claude plan login.)
LEAN_FLAGS = ["--tools", "", "--strict-mcp-config", "--disable-slash-commands", "--setting-sources", "",
              "--no-session-persistence", "--system-prompt", "You extract structured data and reply with JSON only."]


def extract_with_claude(sources: list[Source], model: str = "sonnet", timeout: int = 600) -> dict:
    result = subprocess.run(
        ["claude", "-p", "--model", model, "--output-format", "json", *LEAN_FLAGS],
        input=build_prompt(sources), capture_output=True, text=True, timeout=timeout, cwd="/tmp",
    )
    try:
        envelope = json.loads(result.stdout)
    except json.JSONDecodeError:
        envelope = {}
    # claude -p reports its errors (usage limit, login, ...) in the JSON result, not on stderr.
    if result.returncode != 0 or envelope.get("is_error"):
        reason = envelope.get("result") or result.stderr.strip() or result.stdout.strip()[:300] or f"exit {result.returncode}"
        raise RuntimeError(f"claude -p failed: {reason}")
    return _json_from(envelope.get("result", ""))


RESEARCH_PROMPT = """Research this question on the web for the user and answer it.
Use web search; prefer official sites, GitHub, docs and recent sources. Check claims instead of repeating hype.
Be concise and practical (the user is a developer and student in India). No em dashes.
Return ONLY JSON: {"answer": "<markdown, cite sources inline as [1], [2]>", "sources": [{"title": "...", "url": "..."}]}

Question: """


DEADLINE_SUFFIX = """
Also include "deadline": "YYYY-MM-DD" (the date to act by, from an official source if possible) or null if there
is none or it can't be confirmed. Say in the answer how sure you are."""


def research_with_claude(question: str, model: str = "sonnet", timeout: int = 900, want_deadline: bool = False) -> dict:
    """Web research on your Claude plan: same lean call as extraction, but with web search allowed."""
    flags = list(LEAN_FLAGS)
    flags[flags.index("--tools") + 1] = "WebSearch,WebFetch"
    flags[flags.index("--system-prompt") + 1] = "You are a careful research assistant. Reply with JSON only."
    result = subprocess.run(
        ["claude", "-p", "--model", model, "--output-format", "json", "--allowedTools", "WebSearch,WebFetch", *flags],
        input=RESEARCH_PROMPT + question + (DEADLINE_SUFFIX if want_deadline else ""),
        capture_output=True, text=True, timeout=timeout, cwd="/tmp",
    )
    try:
        envelope = json.loads(result.stdout)
    except json.JSONDecodeError:
        envelope = {}
    if result.returncode != 0 or envelope.get("is_error"):
        reason = envelope.get("result") or result.stderr.strip() or f"exit {result.returncode}"
        raise RuntimeError(f"claude -p failed: {reason}")
    return _json_from(envelope.get("result", ""))
