"""Check items against GitHub: find the real repo, its stars, license, last push, archived.

Uses the `gh` CLI (already logged in on the laptop). Only accepts a repo whose name matches the item.
"""

from __future__ import annotations

import json
import re
import subprocess
from datetime import datetime, timezone

_GITHUB_URL = re.compile(r"github\.com/([\w.-]+)/([\w.-]+)", re.I)


def _squash(s: str) -> str:
    return re.sub(r"[^a-z0-9]", "", s.lower())


def _gh(*args: str) -> list | dict | None:
    r = subprocess.run(["gh", *args], capture_output=True, text=True, timeout=60)
    if r.returncode != 0:
        return None
    return json.loads(r.stdout or "null")


def repo_facts(owner: str, name: str) -> dict | None:
    data = _gh("api", f"repos/{owner}/{name}")
    if not isinstance(data, dict) or "full_name" not in data:
        return None
    return {
        "repo": data["full_name"],
        "stars": data["stargazers_count"],
        "license": (data.get("license") or {}).get("spdx_id"),
        "pushed_at": data["pushed_at"],
        "archived": data["archived"],
        "fork": data["fork"],
        "parent": (data.get("parent") or {}).get("full_name"),
        "description": data.get("description"),
        "checked_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
    }


def find_repo(name: str) -> dict | None:
    """Most-starred repo whose name matches the item name (ignoring case, spaces and dashes)."""
    target = _squash(name)
    if len(target) < 3:
        return None
    hits = _gh("search", "repos", name, "--sort", "stars", "--limit", "8", "--json", "fullName,name")
    for hit in hits or []:
        repo_name = _squash(hit["name"])
        if repo_name == target or (len(target) >= 6 and (target in repo_name or repo_name in target)):
            owner, repo = hit["fullName"].split("/")
            return repo_facts(owner, repo)
    return None


def verify_item(item: dict) -> dict:
    """Fill canonical_key, url, trust and verification for repos/tools that live on GitHub."""
    if item.get("kind") not in ("repo", "tool"):
        return item
    facts = None
    m = _GITHUB_URL.search(item.get("url") or "")
    if m:
        facts = repo_facts(m[1], m[2].removesuffix(".git"))
    elif "not stated" not in item.get("name", "") and not item.get("needs_frames"):
        facts = find_repo(item["name"])
    if not facts:
        return item
    if facts["fork"] and facts["parent"]:  # point at the original, not a copy
        owner, repo = facts["parent"].split("/")
        facts = repo_facts(owner, repo) or facts
    item = dict(item)
    item["url"] = f"https://github.com/{facts['repo']}"
    item["canonical_key"] = f"github:{facts['repo'].lower()}"
    item["verification"] = facts
    # "verified" = the real repo was found and is alive. The reel's claims are fact-checked separately (M3).
    item["trust"] = "dead" if facts["archived"] else "verified"
    return item
