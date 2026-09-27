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


def main() -> None:
    parser = argparse.ArgumentParser(prog="mindcore")
    sub = parser.add_subparsers(required=True)
    p = sub.add_parser("import", help="dry-run a WhatsApp export")
    p.add_argument("export")
    p.add_argument("--out", default="../out/import.json")
    p.set_defaults(func=cmd_import)
    args = parser.parse_args()
    args.func(args)
