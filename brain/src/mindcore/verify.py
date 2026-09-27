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


def name_match(item_name: str, full_repo: str) -> str:
    """How well an item name matches a repo: "exact" or "partial".

    Items may be named "owner/repo"; notes in brackets ("(formerly Schej)") are ignored.
    """
    name = _squash(re.sub(r"\(.*?\)", "", item_name))
    owner, repo = full_repo.lower().split("/")
    return "exact" if name in (_squash(repo), _squash(owner + repo)) else "partial"


# Reels hype popular projects; an exact name that lands on a tiny repo is usually someone else's project.
MIN_STARS_FOR_VERIFIED = 50


def trust_for(facts: dict, item_name: str) -> str:
    if facts["archived"]:
        return "dead"
    if name_match(item_name, facts["repo"]) == "exact" and (facts.get("stars") or 0) >= MIN_STARS_FOR_VERIFIED:
        return "verified"
    return "check"


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
    for exact in (True, False):
        for hit in hits or []:
            repo_name = _squash(hit["name"])
            if (repo_name == target) if exact else (len(target) >= 6 and (target in repo_name or repo_name in target)):
                owner, repo = hit["fullName"].split("/")
                facts = repo_facts(owner, repo)
                if facts:
                    facts["match"] = "exact name" if exact else "partial name"
                return facts
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
    # "verified" = a real, alive, non-tiny repo whose name matches exactly (or the post linked it directly).
    # Anything looser stays "check" so a wrong project never looks confirmed. Claims are fact-checked in M3.
    item["trust"] = "verified" if m and not facts["archived"] else trust_for(facts, item["name"])
    return item
