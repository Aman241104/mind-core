"""Obsidian sync: mind-core notes <-> markdown files in the vault, saved items -> read-only pages.

Everything lives under ~/Obsidian Vault/40 - mind-core/ and nothing outside that folder is ever touched:
  Notes/   your notes and ideas, two-way. Edit in Obsidian or on the phone; the newer side wins, and if both
           changed since the last sync the phone's version is kept as "<title> (phone copy).md" next to yours.
           A new .md you create here becomes a mind-core note. Deleting a file moves the note to the trash.
  Saves/   one page per saved thing (repo, tool, course...), rewritten by mind-core; [[links]] in notes land here.
  mind-core.md   a small Dataview dashboard.

State (what each file looked like at the last sync) is kept in ~/.local/state/mindcore/obsidian.json.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
from pathlib import Path

from .brain import Api

VAULT = Path(os.environ.get("MINDCORE_VAULT", Path.home() / "Obsidian Vault")) / "40 - mind-core"
STATE = Path(os.environ.get("MINDCORE_OBSIDIAN_STATE", Path.home() / ".local/state/mindcore/obsidian.json"))
BAD = re.compile(r'[\\/:*?"<>|#^\[\]]')


def _hash(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()


def _safe(name: str) -> str:
    return BAD.sub("-", name).strip(" .")[:120] or "Untitled"


def _split(text: str) -> tuple[dict[str, str], str]:
    """Frontmatter (flat key: value only) and the body."""
    if text.startswith("---\n"):
        end = text.find("\n---", 4)
        if end > 0:
            meta = {}
            for line in text[4:end].splitlines():
                k, sep, v = line.partition(":")
                if sep:
                    v = v.strip()
                    if v.startswith('"'):
                        try:
                            v = json.loads(v)
                        except ValueError:
                            v = v.strip('"')
                    meta[k.strip()] = v
            return meta, text[end + 4:].lstrip("\n")
    return {}, text


def _q(v: str) -> str:
    return json.dumps(v, ensure_ascii=False)


def _note_file(n: dict) -> str:
    tags = ["mindcore", n["kind"]] + ([f"stage/{n['stage']}"] if n.get("stage") else [])
    meta = [
        "---",
        f"mindcore_id: {n['id']}",
        f"kind: {n['kind']}",
        *([f"stage: {n['stage']}"] if n.get("stage") else []),
        f"title: {_q(n.get('title') or '')}",
        f"updated: {n['updated_at']}",
        f"tags: [{', '.join(tags)}]",
        "---",
        "",
    ]
    return "\n".join(meta) + (n.get("body") or "").rstrip() + "\n"


def _item_file(it: dict) -> str:
    v = it.get("verification") or {}
    meta = [
        "---",
        f"mindcore_id: {it['id']}",
        f"kind: {it['kind']}",
        f"trust: {it['trust']}",
        f"status: {it['status']}",
        *([f"url: {it['url']}"] if it.get("url") else []),
        *([f"stars: {v['stars']}"] if v.get("stars") is not None else []),
        *([f"deadline: {it['deadline']}"] if it.get("deadline") else []),
        f"tags: [mindcore/save, {it['kind']}]",
        "---",
        "",
        f"# {it['name']}",
        "",
        it.get("one_line") or "",
        "",
    ]
    if it.get("user_note"):
        meta += ["## My notes", "", it["user_note"], ""]
    meta += ["> Kept in sync by mind-core. Edits here are overwritten; write in a note and [[link]] this page instead.", ""]
    return "\n".join(meta)


DASHBOARD = """# mind-core

```dataview
TABLE stage, updated FROM "40 - mind-core/Notes" WHERE kind = "idea" AND stage != "done" SORT updated DESC
```

```dataview
TASK FROM "40 - mind-core/Notes" WHERE !completed
```

```dataview
TABLE kind, trust, status FROM "40 - mind-core/Saves" SORT file.mtime DESC LIMIT 25
```
"""


class Sync:
    def __init__(self, api: Api, log=print, dry_run: bool = False) -> None:
        self.api, self.log, self.dry = api, log, dry_run
        self.state: dict = json.loads(STATE.read_text()) if STATE.exists() else {"notes": {}, "items": {}}
        self.counts = {"to_vault": 0, "to_phone": 0, "new_from_vault": 0, "conflicts": 0, "trashed": 0, "removed": 0, "saves": 0}

    # ---------- files ----------

    def _write(self, path: Path, text: str) -> None:
        if self.dry:
            self.log(f"  would write {path.relative_to(VAULT)}")
            return
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_suffix(".tmp")
        tmp.write_text(text)
        tmp.replace(path)

    def _free_name(self, folder: Path, title: str, own: Path | None = None) -> Path:
        base = _safe(title)
        p = folder / f"{base}.md"
        k = 2
        while p.exists() and p != own:
            p = folder / f"{base} {k}.md"
            k += 1
        return p

    # ---------- notes (two-way) ----------

    def notes(self) -> None:
        folder = VAULT / "Notes"
        seen = self.state["notes"]
        server = {n["id"]: n for n in self.api.get("/v1/notes")}
        # Files you renamed or moved within Notes/ are found again by the id in their frontmatter.
        by_id = {}
        if folder.exists():
            for f in folder.glob("*.md"):
                fid = _split(f.read_text())[0].get("mindcore_id")
                if fid:
                    by_id.setdefault(fid, f)
        for nid, rec in seen.items():
            if not Path(rec["path"]).exists() and nid in by_id:
                self.log(f"  renamed in Obsidian: {Path(rec['path']).name} -> {by_id[nid].name}")
                rec["path"] = str(by_id[nid])
                rec["hash"] = ""  # push it: the new file name is the new title

        # 1. Notes that exist on the phone side.
        for nid, summary in server.items():
            rec = seen.get(nid)
            path = Path(rec["path"]) if rec else None
            local = path.read_text() if path and path.exists() else None
            server_changed = not rec or summary["updated_at"] != rec["updated"]
            local_changed = bool(rec) and local is not None and _hash(local) != rec["hash"]

            if rec and local is None:
                # You deleted the file in Obsidian: the note goes to the trash (restorable on the phone).
                self.log(f"  trash: {summary['title'] or nid}")
                if not self.dry:
                    self.api.http.delete(f"/v1/notes/{nid}").raise_for_status()
                    seen.pop(nid)
                self.counts["trashed"] += 1
                continue
            if local_changed and not server_changed:
                self._push(nid, path, local)
                continue
            if not server_changed:
                continue
            full = self.api.get(f"/v1/notes/{nid}")
            if local_changed:
                # Both sides changed: keep yours, park the phone's version next to it.
                copy = self._free_name(folder, f"{full.get('title') or 'Untitled'} (phone copy)")
                self.log(f"  conflict: {path.name} (phone version saved as {copy.name})")
                self._write(copy, _note_file(full).replace(f"mindcore_id: {nid}\n", ""))  # the copy becomes a new note
                self._push(nid, path, local)
                self.counts["conflicts"] += 1
                continue
            want = self._free_name(folder, full.get("title") or f"Untitled {full['kind']} {nid[:6]}", own=path)
            if path and path.exists() and path != want and not self.dry:
                path.rename(want)  # the title changed on the phone
            text = _note_file(full)
            self._write(want, text)
            seen[nid] = {"path": str(want), "hash": _hash(text), "updated": full["updated_at"]}
            self.counts["to_vault"] += 1

        # 2. Notes gone from the phone (trashed there): remove the file if you haven't edited it since.
        for nid in [k for k in seen if k not in server]:
            path = Path(seen[nid]["path"])
            if path.exists() and _hash(path.read_text()) == seen[nid]["hash"]:
                self.log(f"  removed on phone: {path.name}")
                if not self.dry:
                    path.unlink()
                self.counts["removed"] += 1
            if not self.dry:
                seen.pop(nid)

        # 3. New files you wrote in Obsidian become notes.
        if folder.exists():
            known = {Path(r["path"]) for r in seen.values()}
            for path in sorted(folder.glob("*.md")):
                if path in known:
                    continue
                text = path.read_text()
                meta, body = _split(text)
                if meta.get("mindcore_id"):
                    continue  # a synced note whose state was lost; leave it rather than duplicate it
                kind = "idea" if meta.get("kind") == "idea" else "note"
                title = meta.get("title") or path.stem
                self.log(f"  new from Obsidian: {path.name}")
                if self.dry:
                    continue
                n = self.api.post("/v1/notes", {"kind": kind, "title": title, "body": body})
                out = _note_file(n)
                self._write(path, out)
                seen[n["id"]] = {"path": str(path), "hash": _hash(out), "updated": n["updated_at"]}
                self.counts["new_from_vault"] += 1

    def _push(self, nid: str, path: Path, local: str) -> None:
        meta, body = _split(local)
        patch: dict = {"body": body.rstrip() + "\n" if body.strip() else ""}
        # In Obsidian the file name is the title: a renamed file renames the note.
        title = meta.get("title") or ""
        patch["title"] = title if title and _safe(title) == path.stem else path.stem
        if meta.get("stage") in {"spark", "growing", "ready", "done", "parked"}:
            patch["stage"] = meta["stage"]
        self.log(f"  to phone: {path.name}")
        if self.dry:
            return
        r = self.api.http.patch(f"/v1/notes/{nid}", json=patch)
        r.raise_for_status()
        n = r.json()
        out = _note_file(n)  # refresh frontmatter (updated time) so the next run sees no change
        self._write(path, out)
        self.state["notes"][nid] = {"path": str(path), "hash": _hash(out), "updated": n["updated_at"]}
        self.counts["to_phone"] += 1

    # ---------- saved items (one-way) ----------

    def items(self) -> None:
        folder = VAULT / "Saves"
        seen = self.state["items"]
        items = [i for i in self.api.get("/v1/items?shelf=learning&limit=500")]
        names: dict[str, int] = {}
        for it in sorted(items, key=lambda i: i["created_at"]):
            base = _safe(it["name"])
            names[base] = names.get(base, 0) + 1
            path = folder / (f"{base}.md" if names[base] == 1 else f"{base} {names[base]}.md")
            text = _item_file(it)
            h = _hash(text)
            rec = seen.get(it["id"])
            if rec and rec["hash"] == h and Path(rec["path"]) == path and path.exists():
                continue
            if rec and Path(rec["path"]) != path and Path(rec["path"]).exists() and not self.dry:
                Path(rec["path"]).unlink()
            self._write(path, text)
            seen[it["id"]] = {"path": str(path), "hash": h}
            self.counts["saves"] += 1

    def run(self) -> dict:
        if not VAULT.parent.exists():
            raise SystemExit(f"no Obsidian vault at {VAULT.parent}")
        self.notes()
        self.items()
        dash = VAULT / "mind-core.md"
        if not dash.exists():
            self._write(dash, DASHBOARD)
        if not self.dry:
            STATE.parent.mkdir(parents=True, exist_ok=True)
            STATE.write_text(json.dumps(self.state))
        return self.counts
