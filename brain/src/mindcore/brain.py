"""The laptop brain: pull jobs from the mind-core API, fetch + transcribe + extract + verify, send results back.

Config (env, or ~/stash/.secrets/tokens.env): MINDCORE_API (base URL), BRAIN_TOKEN.
"""

from __future__ import annotations

import json
import os
import platform
import signal
import threading
import time
from dataclasses import asdict
from pathlib import Path

import httpx

from .extract import Source, extract_with_claude, research_with_claude
from .fetch import Content, FetchError, fetch
from .verify import verify_item

SECRETS = Path.home() / "stash" / ".secrets" / "tokens.env"
HEARTBEAT_SECONDS = 60
IDLE_SLEEP_SECONDS = 10  # research questions wait on this, so keep it short


def _config() -> tuple[str, str]:
    env = dict(os.environ)
    if SECRETS.exists():
        for line in SECRETS.read_text().splitlines():
            k, _, v = line.partition("=")
            env.setdefault(k.strip(), v.strip())
    api = env.get("MINDCORE_API", "").rstrip("/")
    if not api or not env.get("BRAIN_TOKEN"):
        raise SystemExit("set MINDCORE_API and BRAIN_TOKEN (or put them in ~/stash/.secrets/tokens.env)")
    return api, env["BRAIN_TOKEN"]


class Api:
    def __init__(self) -> None:
        base, token = _config()
        self.http = httpx.Client(base_url=base, headers={"authorization": f"Bearer {token}"}, timeout=60)

    def post(self, path: str, body: object) -> object:
        r = self.http.post(path, json=body)
        r.raise_for_status()
        return r.json()

    def get(self, path: str) -> object:
        r = self.http.get(path)
        r.raise_for_status()
        return r.json()


def process_batch(api: Api, jobs: list[dict], log=print) -> None:
    """Fetch every job, extract all of them in ONE model call, verify, then report each job."""
    fetched: dict[int, tuple[dict, Content]] = {}
    for job in jobs:
        save = job["save"]
        try:
            t0 = time.monotonic()
            fetched[job["id"]] = (job, fetch(save))
            log(f"  fetched {save['url']} ({time.monotonic() - t0:.0f}s)")
        except FetchError as e:
            log(f"  ✗ {save['url']}: {e}")
            api.post("/v1/brain/fail", {"job_id": job["id"], "error": str(e), "retry": e.retry})
        except Exception as e:  # unexpected: report and keep going with the rest of the batch
            log(f"  ✗ {save['url']}: {type(e).__name__}: {e}")
            api.post("/v1/brain/fail", {"job_id": job["id"], "error": f"{type(e).__name__}: {e}", "retry": True})
    if not fetched:
        return

    sources = [
        Source(str(job_id), c.caption, c.transcript, c.creator, c.screen_text,
               shelf_hint=job["save"]["shelf"], note=job["save"].get("note") or "")
        for job_id, (job, c) in fetched.items()
    ]
    try:
        t0 = time.monotonic()
        results = extract_with_claude(sources)
        log(f"  extracted {len(sources)} saves in one call ({time.monotonic() - t0:.0f}s)")
    except Exception as e:
        for job_id in fetched:
            api.post("/v1/brain/fail", {"job_id": job_id, "error": f"extraction failed: {e}", "retry": True})
        log(f"  ✗ extraction failed: {e}")
        return

    for job_id, (job, c) in fetched.items():
        r = results.get(str(job_id)) or {}
        items = [verify_item(it) for it in r.get("items", [])]
        api.post("/v1/brain/complete", {
            "job_id": job_id,
            "save": {
                "creator": c.creator, "caption": c.caption, "transcript": c.transcript, "screen_text": c.screen_text,
                "language": r.get("language") or c.language, "promo": r.get("promo"), "shelf": r.get("shelf"),
            },
            "items": items,
        })
        names = ", ".join(f"{it['name']}{' ✓' if it.get('trust') == 'verified' else ''}" for it in items) or "no items"
        log(f"  ✓ {job['save']['url']} [{r.get('shelf', '?')}]: {names}")


def answer_research(api: Api, q: dict, log=print) -> None:
    log(f"research #{q['id']}: {q['question'][:80]}")
    try:
        t0 = time.monotonic()
        r = research_with_claude(q["question"])
        api.post(f"/v1/brain/research/{q['id']}", {"answer": r.get("answer", ""), "sources": r.get("sources", [])})
        log(f"  ✓ answered in {time.monotonic() - t0:.0f}s with {len(r.get('sources', []))} sources")
    except Exception as e:
        api.post(f"/v1/brain/research/{q['id']}", {"error": str(e)[:500]})
        log(f"  ✗ research failed: {e}")


def run(once: bool = False, batch: int = 6, max_batches: int | None = None, log=print) -> None:
    api = Api()
    stop = False

    def _stop(*_):
        nonlocal stop
        stop = True

    signal.signal(signal.SIGTERM, _stop)
    signal.signal(signal.SIGINT, _stop)
    info = json.dumps({"host": platform.node(), "engines": ["claude"], "pid": os.getpid()})

    # Heartbeat on its own thread: a batch can take minutes and the phone should still see "online".
    def beat() -> None:
        beat_api = Api()
        while not stop:
            try:
                beat_api.post("/v1/brain/heartbeat", info)
            except Exception as e:  # a missed beat just shows "offline" for a minute
                log(f"  heartbeat failed: {e}")
            time.sleep(HEARTBEAT_SECONDS)

    threading.Thread(target=beat, daemon=True).start()
    done_batches = 0
    while not stop and (max_batches is None or done_batches < max_batches):
        # Someone is waiting on research, so it goes before the save backlog.
        q = api.post("/v1/brain/research/claim", {})
        if q:
            answer_research(api, q, log)
            continue
        jobs = api.post("/v1/brain/claim", {"limit": batch})
        if jobs:
            log(f"batch of {len(jobs)}")
            process_batch(api, jobs, log)
            done_batches += 1
            continue
        if once:
            break
        time.sleep(IDLE_SLEEP_SECONDS)


def push_export(export: Path, only_new: bool = True) -> dict:
    """Send a WhatsApp export's links to the API (the API normalizes, sorts and de-duplicates)."""
    from .triage import triage
    from .whatsapp import extract_links, parse_messages, read_export

    text, _media = read_export(export)
    links = extract_links(parse_messages(text))
    # A skipped link means its whole message is a list of the same kind (the API only sees single links).
    skip_msgs = {l.msg_index for l in links if triage(l).shelf == "skip"}
    links = [l for l in links if l.msg_index not in skip_msgs]
    api = Api()
    totals: dict[str, int] = {}
    payload = [{"url": l.raw, "note": l.note, "title": l.title, "source": "whatsapp", "saved_at": l.when.isoformat()}
               for l in links]
    for i in range(0, len(payload), 200):
        counts = api.post("/v1/saves", {"saves": payload[i:i + 200]})
        for k, v in counts.items():
            totals[k] = totals.get(k, 0) + v
    return totals
