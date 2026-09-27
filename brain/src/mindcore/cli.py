"""mindcore command line: `mindcore import <export.zip>` prints the dry run and writes JSON."""

from __future__ import annotations

import argparse
from urllib.parse import quote
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


def cmd_reverify(args: argparse.Namespace) -> None:
    """Re-apply today's stricter rule to items verified earlier: only an exact repo-name match stays verified."""
    from .brain import Api
    from .verify import name_match, trust_for

    api = Api()
    changed = 0
    for it in api.get("/v1/items?limit=500"):
        v = it.get("verification") or {}
        if it["trust"] != "verified" or not v.get("repo") or v.get("match"):
            continue  # nothing to re-check (not verified, not a repo, or already checked by the new rule)
        v["match"] = name_match(it["name"], v["repo"]) + " name"
        new = trust_for(v, it["name"])
        exact = new == "verified"
        print(f"{'keep ' if exact else 'CHECK'}  {it['name'][:40]:40} -> {v['repo']} ★{v.get('stars')}")
        if args.apply:
            api.post(f"/v1/brain/items/{it['id']}", {"trust": new, "verification": v})
        changed += 0 if exact else 1
    print(f"{changed} would move to 'check claims'" if not args.apply else f"moved {changed} to 'check claims'")


def cmd_reindex(args: argparse.Namespace) -> None:
    """Index the text of already-processed saves so Ask can quote them."""
    from .brain import Api

    api = Api()
    while True:
        r = api.post("/v1/brain/reindex", {})
        print(f"indexed {r['saves']} saves ({r['chunks']} chunks), {r['left']} left")
        if not r["saves"]:
            break


def cmd_release(args: argparse.Namespace) -> None:
    """Bump the app version, build it, and publish it so the phone offers the update."""
    import os
    import subprocess

    from .brain import Api

    app = Path.home() / "stash" / "app"
    props = app / "app" / "version.properties"
    v = dict(line.split("=", 1) for line in props.read_text().splitlines() if "=" in line)
    code = int(v["versionCode"]) + 1
    major, minor, patch = (int(x) for x in v["versionName"].split("."))
    name = f"{major}.{minor}.{patch + 1}" if not args.minor else f"{major}.{minor + 1}.0"
    props.write_text(f"versionCode={code}\nversionName={name}\n")
    env = dict(os.environ, JAVA_HOME=str(Path.home() / ".local/share/jdk/jdk-21.0.12.1+1"),
               ANDROID_HOME=str(Path.home() / "Android/Sdk"))
    print(f"building {name} ({code})…")
    # Release build: minified (~3 MB instead of ~22 MB), signed with the same key as the debug builds.
    r = subprocess.run(["./gradlew", "-q", "assembleRelease"], cwd=app, env=env, capture_output=True, text=True)
    if r.returncode != 0:
        props.write_text(f"versionCode={v['versionCode']}\nversionName={v['versionName']}\n")  # undo the bump
        raise SystemExit("build failed:\n" + (r.stdout + r.stderr)[-2000:])
    apk = app / "app/build/outputs/apk/release/app-release.apk"
    api = Api()
    import httpx

    # Slow or flaky uplinks happen (a write once stalled past 60 s); retry a few times before giving up.
    for attempt in range(3):
        try:
            resp = api.http.post("/v1/brain/app", content=apk.read_bytes(), timeout=httpx.Timeout(600.0), headers={
                "content-type": "application/vnd.android.package-archive",
                "x-version-code": str(code), "x-version-name": name, "x-notes": quote(args.notes or ""),
            })
            break
        except httpx.TransportError as e:
            print(f"upload attempt {attempt + 1} failed ({type(e).__name__}), retrying…")
    else:
        raise SystemExit("upload failed 3 times; the build is ready, run release again later")
    if resp.status_code >= 400:
        raise SystemExit(f"upload failed: {resp.text}")
    print(f"published {name} ({code}), {apk.stat().st_size // 1024} KB. The phone will offer it on next open.")


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
    p = sub.add_parser("reverify", help="re-check earlier 'verified' repos with the exact-name rule")
    p.add_argument("--apply", action="store_true", help="actually change them (default: dry run)")
    p.set_defaults(func=cmd_reverify)
    p = sub.add_parser("reindex", help="index processed saves' text for Ask")
    p.set_defaults(func=cmd_reindex)
    p = sub.add_parser("release", help="build the app and publish it as an in-app update")
    p.add_argument("--notes", help="what's new (shown in the update banner)")
    p.add_argument("--minor", action="store_true", help="bump 0.x instead of 0.x.y")
    p.set_defaults(func=cmd_release)
    p = sub.add_parser("status", help="show API status: laptop, jobs, saves, items")
    p.set_defaults(func=cmd_status)
    args = parser.parse_args()
    args.func(args)
