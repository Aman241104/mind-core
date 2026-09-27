"""Parse a WhatsApp chat export (.zip or .txt) into saves: links, media and notes.

Handles both export styles:
  iOS / converter style:  [3/21/26, 11:13:43 AM] You: text
  Android style:          21/03/2026, 11:13 am - You: text
Multi-line messages are joined to the message that started them.
"""

from __future__ import annotations

import hashlib
import re
import zipfile
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

# Invisible marks WhatsApp puts before "<image omitted>" etc.
_INVISIBLE = dict.fromkeys(map(ord, "‎‏‪‬⁨⁩﻿"), None)

_IOS_LINE = re.compile(
    r"^\[(?P<date>\d{1,2}/\d{1,2}/\d{2,4}), (?P<time>\d{1,2}:\d{2}(?::\d{2})?\s?[AaPp]\.?[Mm]\.?)\] "
    r"(?P<author>[^:]+): (?P<text>.*)$"
)
_ANDROID_LINE = re.compile(
    r"^(?P<date>\d{1,2}/\d{1,2}/\d{2,4}), (?P<time>\d{1,2}:\d{2}(?:\s?[AaPp]\.?[Mm]\.?)?) - "
    r"(?P<author>[^:]+): (?P<text>.*)$"
)
_URL = re.compile(r"https?://[^\s<>\"')\]]+")
_MEDIA = re.compile(
    r"<(?P<kind>image|video|audio|document|sticker|GIF) omitted>\s*(?P<name>.*)"
    r"|(?P<file>[\w-]+\.(?:jpe?g|png|webp|mp4|opus|pdf|gif)) \(file attached\)",
    re.I,
)
_FORWARDED = "[Forwarded]"

# Query params that only track the sharer; dropping them makes duplicates match.
_TRACKING = re.compile(r"^(utm_.*|igsh|igshid|igsi|stkn|si|fbclid|gclid|ref|ref_src|s|t|_vercel_share|img_index|feature|org)$")


@dataclass
class Message:
    when: datetime
    author: str
    text: str
    forwarded: bool = False
    media: str | None = None  # "image", "video", ... when this message is an attachment
    media_file: str | None = None


@dataclass
class Link:
    url: str  # normalized
    raw: str
    host: str
    when: datetime
    note: str  # the message text around the link, minus URLs
    title: str | None  # heading line of a list of links, e.g. "Muslim Site"
    forwarded: bool
    msg_index: int  # links from one message share this, e.g. a list of sites
    id: str = field(init=False)

    def __post_init__(self) -> None:
        self.id = hashlib.sha1(self.url.encode()).hexdigest()[:16]


def _parse_when(date: str, time: str, day_first: bool) -> datetime:
    a, b, y = date.split("/")
    month, day = (int(b), int(a)) if day_first else (int(a), int(b))
    year = int(y) + 2000 if len(y) == 2 else int(y)
    t = re.sub(r"[.\s]", "", time).upper()
    fmt = "%I:%M:%S%p" if t.count(":") == 2 else "%I:%M%p" if t.endswith("M") else "%H:%M"
    clock = datetime.strptime(t, fmt)
    return datetime(year, month, day, clock.hour, clock.minute, clock.second)


def _guess_day_first(lines: list[str], pattern: re.Pattern[str]) -> bool:
    """A first number above 12 anywhere means dates are day/month."""
    for line in lines:
        m = pattern.match(line)
        if m:
            a, b, _ = m["date"].split("/")
            if int(a) > 12:
                return True
            if int(b) > 12:
                return False
    return pattern is _ANDROID_LINE  # Android exports default to the phone locale; India is d/m


def parse_messages(text: str) -> list[Message]:
    lines = text.translate(_INVISIBLE).splitlines()
    pattern = _IOS_LINE if any(_IOS_LINE.match(l) for l in lines[:50]) else _ANDROID_LINE
    day_first = _guess_day_first(lines, pattern)
    messages: list[Message] = []
    for line in lines:
        m = pattern.match(line)
        if not m:
            if messages:  # continuation of the previous message
                messages[-1].text += "\n" + line
            continue
        body = m["text"]
        forwarded = body.startswith(_FORWARDED)
        if forwarded:
            body = body[len(_FORWARDED):].lstrip()
        msg = Message(_parse_when(m["date"], m["time"], day_first), m["author"].strip(), body, forwarded)
        media = _MEDIA.match(body)
        if media:
            msg.media = (media["kind"] or "file").lower()
            msg.media_file = (media["name"] or media["file"] or "").strip() or None
        messages.append(msg)
    return messages


def normalize_url(raw: str) -> str:
    raw = raw.rstrip(".,;:!?*_")
    parts = urlsplit(raw)
    host = parts.netloc.lower().removeprefix("www.").removeprefix("m.")
    path = parts.path
    if host in ("instagram.com", "instagr.am"):
        # /reel/<code>/, /p/<code>/ and /<user>/reel/<code>/ are the same post
        m = re.search(r"/(reel|reels|p|tv)/([\w-]+)", path)
        if m:
            kind = "reel" if m[1] in ("reel", "reels", "tv") else "p"
            path = f"/{kind}/{m[2]}/"
    if host in ("youtu.be",):
        host, path = "youtube.com", "/watch"
        query = urlencode({"v": parts.path.strip("/")})
    else:
        query = urlencode([(k, v) for k, v in parse_qsl(parts.query) if not _TRACKING.match(k)])
    if path not in ("", "/"):
        path = path.rstrip("/") + ("/" if host == "instagram.com" else "")
    return urlunsplit(("https", host, path or "/", query, ""))


def extract_links(messages: list[Message]) -> list[Link]:
    links: dict[str, Link] = {}
    for i, msg in enumerate(messages):
        urls = _URL.findall(msg.text)
        if not urls:
            continue
        # Headings like "Muslim Site" sit on their own line above a group of links.
        title = None
        for line in msg.text.splitlines():
            stripped = line.strip().strip("*_ -:")
            line_urls = _URL.findall(line)
            if stripped and not line_urls:
                title = stripped[:120]
            for raw in line_urls:
                url = normalize_url(raw)
                if url in links:
                    continue
                note = _URL.sub("", msg.text).strip()
                links[url] = Link(url, raw, urlsplit(url).netloc, msg.when, note[:500], title, msg.forwarded, i)
    return list(links.values())


def read_export(path: Path) -> tuple[str, dict[str, bytes]]:
    """Return the chat text and any media files (name -> bytes) from a .zip or .txt."""
    if path.suffix.lower() != ".zip":
        return path.read_text(encoding="utf-8", errors="replace"), {}
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        txt = [n for n in names if n.lower().endswith(".txt")]
        if not txt:
            raise ValueError(f"no .txt chat file in {path}")
        chat = max(txt, key=lambda n: z.getinfo(n).file_size)
        media = {Path(n).name: z.read(n) for n in names if not n.lower().endswith((".txt", ".md")) and not n.endswith("/")}
        return z.read(chat).decode("utf-8", errors="replace"), media
