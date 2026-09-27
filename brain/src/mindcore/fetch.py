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


def fetch_image(url: str) -> Content:
    """A screenshot on Cloudinary: download it and read its text."""
    resp = httpx.get(url, timeout=60, follow_redirects=True)
    if resp.status_code >= 400:
        raise FetchError(f"image returned HTTP {resp.status_code}", retry=resp.status_code >= 500)
    with tempfile.NamedTemporaryFile(suffix=".img") as f:
        f.write(resp.content)
        f.flush()
        out = subprocess.run(["tesseract", f.name, "-", "--psm", "3"], capture_output=True, text=True, timeout=90)
    return Content(screen_text=re.sub(r"[ \t]+", " ", out.stdout).strip()[:8000])


def _vtt_text(vtt: str) -> str:
    """Captions → plain text. Auto-captions repeat each line as it scrolls, so drop consecutive repeats."""
    out: list[str] = []
    for line in vtt.splitlines():
        line = re.sub(r"<[^>]+>", "", line).strip()
        if not line or "-->" in line or line.startswith(("WEBVTT", "Kind:", "Language:", "NOTE")) or line.isdigit():
            continue
        if not out or out[-1] != line:
            out.append(line)
    return " ".join(out)


def fetch_youtube(url: str) -> Content:
    """A YouTube video: its own captions + chapters (no audio download for long videos). Short videos with no
    captions fall back to downloading the audio for Whisper."""
    with tempfile.TemporaryDirectory(prefix="mindcore-") as tmp:
        work = Path(tmp)
        r = _yt_dlp("--skip-download", "--write-info-json", "--write-subs", "--write-auto-subs",
                    "--sub-langs", "en,en-orig,hi,hi-orig", "--sub-format", "vtt",
                    # A caption track that fails (YouTube rate-limits these) shouldn't sink the whole save.
                    "--ignore-errors", "-o", str(work / "v.%(ext)s"), url)
        info_file = next(work.glob("*.info.json"), None)
        if not info_file:
            err = (r.stderr.strip().splitlines() or ["yt-dlp failed"])[-1]
            raise FetchError(err[:300], retry="Private" not in err and "unavailable" not in err)
        info = json.loads(info_file.read_text())
        subs = sorted(work.glob("*.vtt"), key=lambda p: (".en" not in p.name, "orig" in p.name))
        transcript = _vtt_text(subs[0].read_text(errors="replace")) if subs else ""
        if not transcript and (info.get("duration") or 0) <= 600:
            return fetch_video(url)  # short and no captions: transcribe the audio instead
        chapters = [f"{int(c['start_time'] // 60)}:{int(c['start_time'] % 60):02d} {c['title']}" for c in info.get("chapters") or []]
        minutes = round((info.get("duration") or 0) / 60)
        return Content(
            caption=f"{info.get('title', '')}\n{info.get('description') or ''}"[:3000],
            transcript=transcript[:12000],
            screen_text=(f"YouTube video, {minutes} min. Chapters:\n" + "\n".join(chapters)) if chapters else f"YouTube video, {minutes} min.",
            creator=info.get("channel") or info.get("uploader"),
            language=info.get("language"),
            extra={"duration": info.get("duration"), "resource": "video"},
        )


def fetch_playlist(url: str) -> Content:
    """A YouTube playlist: title, channel and the list of videos, saved as one learning resource."""
    r = subprocess.run(["uvx", "-q", "yt-dlp@latest", "--flat-playlist", "-J", url], capture_output=True, text=True, timeout=180)
    if r.returncode != 0:
        raise FetchError((r.stderr.strip().splitlines() or ["yt-dlp failed"])[-1][:300], retry=True)
    d = json.loads(r.stdout)
    entries = d.get("entries") or []
    total_min = round(sum(e.get("duration") or 0 for e in entries) / 60)
    lines = [f"{i + 1}. {e.get('title')} ({round((e.get('duration') or 0) / 60)} min)" for i, e in enumerate(entries[:80])]
    return Content(
        caption=f"{d.get('title', '')}\n{d.get('description') or ''}"[:3000],
        screen_text=f"YouTube playlist, {len(entries)} videos, about {total_min} min in total:\n" + "\n".join(lines),
        creator=d.get("channel") or d.get("uploader"),
        extra={"videos": len(entries), "resource": "playlist"},
    )


def _cloudinary_download_url(url: str) -> str:
    """Cloudinary blocks public delivery of PDFs on new free accounts, so fetch our own uploads with a signed,
    private download link (Admin API) made from the key in ~/stash/.secrets/cloudinary.env."""
    import hashlib
    import time
    from urllib.parse import urlencode

    m = re.match(r"https://res\.cloudinary\.com/([^/]+)/raw/upload/(?:v\d+/)?(.+)$", url)
    secrets = Path.home() / "stash" / ".secrets" / "cloudinary.env"
    if not m or not secrets.exists():
        return url
    env = dict(line.split("=", 1) for line in secrets.read_text().splitlines() if "=" in line)
    params = {"public_id": m[2], "timestamp": str(int(time.time())), "type": "upload"}
    to_sign = "&".join(f"{k}={params[k]}" for k in sorted(params)) + env["CLOUDINARY_SECRET"]
    params["signature"] = hashlib.sha1(to_sign.encode()).hexdigest()
    params["api_key"] = env["CLOUDINARY_KEY"]
    return f"https://api.cloudinary.com/v1_1/{m[1]}/raw/download?{urlencode(params)}"


def fetch_pdf(url: str) -> Content:
    """A book or paper as a PDF: title and page count, plus the text of the first 30 pages (contents, intro)."""
    resp = httpx.get(_cloudinary_download_url(url), timeout=120, follow_redirects=True, headers={"user-agent": UA})
    if resp.status_code >= 400:
        raise FetchError(f"PDF returned HTTP {resp.status_code}", retry=resp.status_code >= 500)
    with tempfile.NamedTemporaryFile(suffix=".pdf") as f:
        f.write(resp.content)
        f.flush()
        meta = subprocess.run(["pdfinfo", f.name], capture_output=True, text=True, timeout=60).stdout
        text = subprocess.run(["pdftotext", "-f", "1", "-l", "30", "-layout", f.name, "-"], capture_output=True, text=True, timeout=120).stdout
    info = dict(line.split(":", 1) for line in meta.splitlines() if ":" in line)
    title = info.get("Title", "").strip()
    pages = info.get("Pages", "?").strip()
    return Content(
        caption=f"PDF book/document. Title: {title or 'unknown'}. Author: {info.get('Author', '').strip() or 'unknown'}. {pages} pages.",
        screen_text=re.sub(r"[ \t]+", " ", text)[:15000],
        extra={"pages": pages, "resource": "book"},
    )


def fetch(save: dict) -> Content:
    kind, url = save["kind_hint"], save["url"]
    if kind == "video":
        return fetch_youtube(url)
    if kind == "playlist":
        return fetch_playlist(url)
    if kind == "pdf":
        return fetch_pdf(url)
    if kind == "text":  # a typed or shared note: the note itself is the content
        return Content(screen_text=save.get("note") or "")
    if kind == "image":
        return fetch_image(url)
    if kind in ("reel", "post"):
        return fetch_video(url) if kind == "reel" or "youtube.com" in url else fetch_post(url)
    if kind == "chat_share":
        if "chatgpt.com" in url:
            return fetch_chatgpt_share(url)
        # Claude/Gemini share pages need a real browser; the phone's WebView handles those (M2).
        raise FetchError("needs the phone's browser (Claude/Gemini share page)")
    return fetch_page(url)


def fetch_post(url: str) -> Content:
    """Instagram photo/carousel posts: caption from metadata, text read off every image.

    Carousels that contain videos are handled as reels.
    """
    with tempfile.TemporaryDirectory(prefix="mindcore-") as tmp:
        work = Path(tmp)
        r = _yt_dlp("--ignore-no-formats-error", "--skip-download", "--write-info-json", "--write-thumbnail",
                    "-o", str(work / "p_%(playlist_index|0)s.%(ext)s"), url)
        infos = sorted(work.glob("*.info.json"))
        if not infos:
            err = (r.stderr.strip().splitlines() or ["yt-dlp failed"])[-1]
            raise FetchError(err[:300], retry="empty media" not in err and "not available" not in err)
        metas = [json.loads(f.read_text()) for f in infos]
        if any(m.get("vcodec") not in (None, "none") and m.get("duration") for m in metas):
            return fetch_video(url)
        top = next((m for m in metas if m.get("description")), metas[0])
        texts = []
        for img in sorted(p for p in work.iterdir() if p.suffix in (".jpg", ".jpeg", ".png", ".webp")):
            out = subprocess.run(["tesseract", str(img), "-", "--psm", "3"], capture_output=True, text=True, timeout=60)
            text = re.sub(r"\s+", " ", out.stdout).strip()
            if len(text) > 15:
                texts.append(text)
        return Content(caption=top.get("description") or "", screen_text="\n".join(texts),
                       creator=top.get("uploader") or top.get("channel"))
