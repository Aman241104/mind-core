"""mindcore command line: `mindcore import <export.zip>` prints the dry run and writes JSON."""

from __future__ import annotations

import argparse
import json
from collections import Counter
from dataclasses import asdict
from pathlib import Path

from .triage import triage
from .whatsapp import extract_links, parse_messages, read_export


def cmd_import(args: argparse.Namespace) -> None:
    text, media = read_export(Path(args.export))
    messages = parse_messages(text)
    links = extract_links(messages)
    results = [(link, triage(link)) for link in links]
    # One skipped link means the whole message is a list of the same kind of thing.
    skip_msgs = {link.msg_index for link, t in results if t.shelf == "skip"}
    rows = []
    for link, t in results:
        if link.msg_index in skip_msgs:
            continue
        row = asdict(link) | {"shelf": t.shelf, "reason": t.reason, "mine": t.mine}
        row["when"] = link.when.isoformat()
        rows.append(row)
    skipped = len(links) - len(rows)
    media_msgs = Counter(m.media for m in messages if m.media)

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(rows, indent=1, ensure_ascii=False))

    shelves = Counter(r["shelf"] for r in rows)
    hosts = Counter(r["host"] for r in rows if r["shelf"] == "learning")
    print(f"{len(messages)} messages, {messages[0].when:%d %b %Y} → {messages[-1].when:%d %b %Y}")
    print(f"{len(links)} unique links: {len(rows)} kept, {skipped} skipped")
    print("shelves: " + ", ".join(f"{k} {v}" for k, v in shelves.most_common()))
    print("learning sources: " + ", ".join(f"{h} {n}" for h, n in hosts.most_common(8)))
    print(f"media in chat: {dict(media_msgs)}; media files in export: {len(media)}")
    print(f"wrote {out}")


def cmd_push(args: argparse.Namespace) -> None:
    from .brain import push_export

    print(push_export(Path(args.export)))


def cmd_brain(args: argparse.Namespace) -> None:
    from .brain import run

    run(once=args.once, batch=args.batch, max_batches=args.batches)


def cmd_pair(args: argparse.Namespace) -> None:
    """Send the server address + phone key to the app over adb, so nothing is typed on the phone."""
    import os
    import subprocess
    from urllib.parse import urlencode

    from .brain import SECRETS

    env = dict(line.split("=", 1) for line in SECRETS.read_text().splitlines() if "=" in line)
    link = "mindcore://pair?" + urlencode({"url": env["MINDCORE_API"], "token": env["API_TOKEN"]})
    cmd = ["adb"] + (["-s", args.serial] if args.serial else []) + [
        "shell", f"am start -a android.intent.action.VIEW -d '{link}' app.mindcore"]
    r = subprocess.run(cmd, capture_output=True, text=True, env=os.environ)
    print("sent pairing link to the phone" if r.returncode == 0 and "Error" not in r.stdout + r.stderr
          else f"failed: {(r.stdout + r.stderr).strip()}")


def cmd_status(args: argparse.Namespace) -> None:
    from .brain import Api

    print(json.dumps(Api().get("/v1/status"), indent=1))


def main() -> None:
    parser = argparse.ArgumentParser(prog="mindcore")
    sub = parser.add_subparsers(required=True)
    p = sub.add_parser("import", help="dry-run a WhatsApp export")
    p.add_argument("export")
    p.add_argument("--out", default="../out/import.json")
    p.set_defaults(func=cmd_import)
    p = sub.add_parser("push", help="send a WhatsApp export's links to the mind-core API")
    p.add_argument("export")
    p.set_defaults(func=cmd_push)
    p = sub.add_parser("brain", help="process jobs from the API (runs until stopped)")
    p.add_argument("--once", action="store_true", help="exit when the queue is empty")
    p.add_argument("--batch", type=int, default=6, help="saves per extraction call")
    p.add_argument("--batches", type=int, default=None, help="stop after this many batches")
    p.set_defaults(func=cmd_brain)
    p = sub.add_parser("pair", help="pair the phone app with this server over adb")
    p.add_argument("--serial", help="adb device serial (when more than one is connected)")
    p.set_defaults(func=cmd_pair)
    p = sub.add_parser("status", help="show API status: laptop, jobs, saves, items")
    p.set_defaults(func=cmd_status)
    args = parser.parse_args()
    args.func(args)
