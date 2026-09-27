"""Get the content behind a save: reel audio + caption + on-screen text, web page text, ChatGPT share chats."""

from __future__ import annotations

import json
import re
import subprocess
import tempfile
from dataclasses import dataclass, field
from pathlib import Path

import httpx

from .transcribe import transcribe

UA = "Mozilla/5.0 (Linux; Android 16; Pixel 7a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"


class FetchError(Exception):
    def __init__(self, message: str, retry: bool = False):
        super().__init__(message)
        self.retry = retry


@dataclass
class Content:
    caption: str = ""
    transcript: str = ""
    screen_text: str = ""  # OCR of video frames or page text
    creator: str | None = None
    language: str | None = None
    extra: dict = field(default_factory=dict)


def _yt_dlp(*args: str, timeout: int = 180) -> subprocess.CompletedProcess:
    # uvx keeps yt-dlp at the latest release; Instagram breaks old versions often.
    return subprocess.run(["uvx", "-q", "yt-dlp@latest", "--no-progress", *args],
                          capture_output=True, text=True, timeout=timeout)


def _ocr_frames(video: Path, workdir: Path, max_frames: int = 6) -> str:
    """Grab frames where the picture changes and read their text; reels often show the repo name only on screen."""
    frames = workdir / "frames"
    frames.mkdir()
    subprocess.run(["ffmpeg", "-loglevel", "error", "-i", str(video), "-vf", "select='gt(scene,0.25)',scale=720:-1",
                    "-fps_mode", "vfr", "-frames:v", str(max_frames), str(frames / "f_%02d.jpg")],
                   capture_output=True, timeout=120)
    if not any(frames.iterdir()):  # no scene changes: take one frame from the middle
        subprocess.run(["ffmpeg", "-loglevel", "error", "-ss", "3", "-i", str(video), "-frames:v", "1",
                        str(frames / "f_01.jpg")], capture_output=True, timeout=60)
    texts = []
    for f in sorted(frames.glob("*.jpg")):
        out = subprocess.run(["tesseract", str(f), "-", "--psm", "3"], capture_output=True, text=True, timeout=60)
        text = re.sub(r"\s+", " ", out.stdout).strip()
        if len(text) > 15 and text not in texts:
            texts.append(text)
    return "\n".join(texts)


def fetch_video(url: str) -> Content:
    with tempfile.TemporaryDirectory(prefix="mindcore-") as tmp:
        work = Path(tmp)
        r = _yt_dlp("-f", "bv*[height<=720]+ba/b[height<=720]/b", "--merge-output-format", "mp4",
                    "--write-info-json", "-o", str(work / "v.%(ext)s"), url)
        if r.returncode != 0:
            err = (r.stderr.strip().splitlines() or ["yt-dlp failed"])[-1]
            # Private/removed posts won't fix themselves; network hiccups might.
            permanent = any(s in err for s in ("empty media", "not available", "Private", "login", "404"))
            raise FetchError(err[:300], retry=not permanent)
        info = json.loads((work / "v.info.json").read_text())
        video = next(work.glob("v.mp4"), None) or next(p for p in work.iterdir() if p.suffix in (".mp4", ".webm", ".mkv"))
        t = transcribe(video)
        return Content(
            caption=info.get("description") or info.get("title") or "",
            transcript=t.text,
            screen_text=_ocr_frames(video, work),
            creator=info.get("uploader") or info.get("channel"),
            language=t.language,
            extra={"duration": info.get("duration")},
        )


def fetch_page(url: str) -> Content:
    import trafilatura

    try:
        resp = httpx.get(url, headers={"user-agent": UA}, follow_redirects=True, timeout=30)
    except httpx.HTTPError as e:
        raise FetchError(f"page fetch failed: {e}", retry=True) from e
    if resp.status_code >= 400:
        raise FetchError(f"page returned HTTP {resp.status_code}", retry=resp.status_code >= 500)
    text = trafilatura.extract(resp.text, url=url, include_links=True) or ""
    title = re.search(r"<title[^>]*>(.*?)</title>", resp.text, re.S | re.I)
    return Content(caption=(title[1].strip() if title else ""), screen_text=text[:12000])


def fetch_chatgpt_share(url: str) -> Content:
    """ChatGPT share pages embed the whole chat (React Router turbo-stream), so one request is enough."""
    resp = httpx.get(url, headers={"user-agent": UA}, follow_redirects=True, timeout=30)
    if resp.status_code >= 400:
        raise FetchError(f"share page returned HTTP {resp.status_code}")
    chunks = re.findall(r'streamController\.enqueue\((".*?")\);', resp.text, re.S)
    decoded = "".join(json.loads(c) for c in chunks)
    try:
        arr = json.loads(decoded.split("\n")[0])
    except (json.JSONDecodeError, IndexError) as e:
        raise FetchError("could not decode the ChatGPT share page") from e
    # Message texts are the long strings in the flattened payload, in order.
    texts = [x for x in arr if isinstance(x, str) and len(x) > 80 and not x.startswith(("http", "{"))]
    title = re.search(r"<title>(.*?)</title>", resp.text)
    return Content(caption=title[1] if title else "ChatGPT chat", screen_text="\n\n---\n\n".join(texts)[:20000])


def fetch(save: dict) -> Content:
    kind, url = save["kind_hint"], save["url"]
    if kind in ("reel", "post"):
        return fetch_video(url) if kind == "reel" or "youtube.com" in url else fetch_post(url)
    if kind == "chat_share":
        if "chatgpt.com" in url:
            return fetch_chatgpt_share(url)
        # Claude/Gemini share pages need a real browser; the phone's WebView handles those (M2).
        raise FetchError("needs the phone's browser (Claude/Gemini share page)")
    return fetch_page(url)


def fetch_post(url: str) -> Content:
    """Instagram photo/carousel posts: caption via yt-dlp metadata; images get OCR'd."""
    with tempfile.TemporaryDirectory(prefix="mindcore-") as tmp:
        work = Path(tmp)
        r = _yt_dlp("--write-info-json", "--skip-download", "-o", str(work / "p.%(ext)s"), url)
        info_file = next(work.glob("*.info.json"), None)
        if r.returncode != 0 and not info_file:
            # Carousels of images often aren't "videos" to yt-dlp; fall back to the public page's text.
            try:
                return fetch_page(url)
            except FetchError:
                err = (r.stderr.strip().splitlines() or ["yt-dlp failed"])[-1]
                raise FetchError(err[:300]) from None
        info = json.loads(info_file.read_text()) if info_file else {}
        if info.get("_type") == "playlist" or info.get("ext") in ("mp4", "webm"):
            return fetch_video(url)
        return Content(caption=info.get("description") or "", creator=info.get("uploader"))
