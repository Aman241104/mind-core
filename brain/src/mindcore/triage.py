"""First-pass sort of links into shelves: learning, work, skip, or unsure (left for the AI).

Rules only; anything they can't decide is "unsure" and goes to a model later.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

from .whatsapp import Link

# GitHub owners that are you / your team: their repos are your own work, not finds.
OWN_GITHUB = {"aman241104", "mehtatechteam"}

# Dropped entirely on import: not shown anywhere, not even on the Work shelf.
SKIP = re.compile(r"astro|love.?probl|lovebackexpert|marriage|addword|adword", re.I)

LEARNING_HOSTS = {
    "instagram.com", "youtube.com", "x.com", "twitter.com", "linkedin.com", "reddit.com",
    "medium.com", "dev.to", "huggingface.co", "arxiv.org", "coursera.org", "udemy.com",
    "roadmap.sh", "chatgpt.com", "claude.ai", "gemini.google.com", "share.gemini.google",
    "perplexity.ai", "t.me",
}
WORK_HOSTS = {"meet.google.com", "docs.google.com", "drive.google.com", "sheets.google.com", "calendar.google.com"}
# Deploy previews are almost always your own client builds.
WORK_HOST_PATTERNS = [re.compile(p) for p in (r"\.vercel\.app$", r"\.netlify\.app$", r"\.pages\.dev$", r"\.myshopify\.com$")]
WORK_WORDS = re.compile(
    r"\b(client|invoice|quotation|seo|meta title|alt tags?|live site|deploy|demo|walkthrough|payment|proposal)\b",
    re.I,
)
LEARNING_WORDS = re.compile(r"\b(learn|course|cert|roadmap|tutorial|repo|tool|try this|interview|dsa|free)\b", re.I)


@dataclass
class Triage:
    shelf: str  # "learning" | "work" | "skip" | "unsure"
    reason: str
    mine: bool = False


def triage(link: Link) -> Triage:
    host, path = link.host, link.url.split(link.host, 1)[1]
    context = f"{link.title or ''} {link.note}"

    if SKIP.search(host) or SKIP.search(link.title or ""):
        return Triage("skip", "on the skip list")
    if host == "github.com":
        owner = path.strip("/").split("/")[0].lower() if path.strip("/") else ""
        if owner in OWN_GITHUB:
            return Triage("work", f"your own repo ({owner})", mine=True)
        return Triage("learning", "someone else's GitHub repo")
    if host in WORK_HOSTS:
        return Triage("work", f"{host} is a work tool")
    if any(p.search(host) for p in WORK_HOST_PATTERNS):
        return Triage("work", "deploy preview of a site you built", mine=True)
    if WORK_WORDS.search(context):
        return Triage("work", f"work words in note: {WORK_WORDS.search(context)[0]!r}")
    if host in LEARNING_HOSTS:
        return Triage("learning", f"{host} is where you save finds")
    if LEARNING_WORDS.search(context):
        return Triage("learning", f"learning words in note: {LEARNING_WORDS.search(context)[0]!r}")
    return Triage("unsure", "unknown site, no clues in the note")
