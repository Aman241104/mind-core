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
- kind is one of: repo, tool, cert, course, job, tip, other.
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
- shelf: "learning" if it's something to learn/try/apply to, "work" if it's the owner's own client/business
  material (client websites, suppliers, competitor research for a client). Sources marked shelf_hint=learning
  are almost always learning; decide carefully for shelf_hint=unsure. Work sources get no items.

Return ONLY JSON: {"<source id>": {"shelf": "learning"|"work", "items": [{"kind","name","url","one_line",
"claims":[...], "needs_frames": bool}], "promo": bool, "language": "<lang>"}}

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


def build_prompt(sources: list[Source]) -> str:
    parts = [
        f"### {s.id}\nshelf_hint: {s.shelf_hint}\ncreator: {s.creator or '?'}\nyour note: {s.note[:300]}\n"
        f"caption: {s.caption[:1500]}\ntranscript: {s.transcript[:6000]}\nscreen text: {s.screen_text[:4000]}"
        for s in sources
    ]
    return PROMPT + "\n\n".join(parts)


def _json_from(text: str) -> dict:
    m = re.search(r"\{.*\}", text, re.S)
    if not m:
        raise ValueError(f"no JSON in model output: {text[:200]!r}")
    return json.loads(m[0])


def extract_with_claude(sources: list[Source], model: str = "sonnet", timeout: int = 600) -> dict:
    result = subprocess.run(
        ["claude", "-p", "--model", model, "--output-format", "json"],
        input=build_prompt(sources), capture_output=True, text=True, timeout=timeout,
    )
    if result.returncode != 0:
        raise RuntimeError(f"claude -p failed: {result.stderr[-500:]}")
    envelope = json.loads(result.stdout)
    return _json_from(envelope.get("result", ""))
