#!/usr/bin/env python3
"""Carries the bookmarks over from the Firefox-based SuperAwesomeMagical Browser (2026.11 and
earlier) to the Ladybird one: reads the old profile's places.sqlite and writes Ladybird's
Bookmarks.json. The launcher runs it once, when the Ladybird profile is new.

    firefox-bookmarks.py <firefox profile folder> <Bookmarks.json to write>

The toolbar's bookmarks become the top level (Ladybird's bookmarks bar); the Bookmarks Menu,
Other Bookmarks and Mobile Bookmarks become folders, when they have anything in them.
"""
import json
import shutil
import sqlite3
import sys
import tempfile
import uuid
from pathlib import Path

ROOTS = [
    ("toolbar_____", None),
    ("menu________", "Bookmarks Menu"),
    ("unfiled_____", "Other Bookmarks"),
    ("mobile______", "Mobile Bookmarks"),
]
KEEP_SCHEMES = ("http:", "https:", "ftp:", "file:")


def load(places: Path):
    # Firefox may still have the database open (or have left a write-ahead log): read a copy.
    with tempfile.TemporaryDirectory() as tmp:
        for suffix in ("", "-wal", "-shm"):
            source = places.with_name(places.name + suffix)
            if source.exists():
                shutil.copy2(source, Path(tmp) / (places.name + suffix))
        db = sqlite3.connect(Path(tmp) / places.name)
        rows = db.execute(
            "SELECT b.id, b.type, b.parent, b.position, b.title, b.dateAdded, b.lastModified, b.guid, p.url"
            " FROM moz_bookmarks b LEFT JOIN moz_places p ON p.id = b.fk"
        ).fetchall()
        db.close()
    return rows


def convert(rows):
    children, by_guid = {}, {}
    for row in rows:
        children.setdefault(row[2], []).append(row)
        by_guid[row[7]] = row
    for siblings in children.values():
        siblings.sort(key=lambda r: r[3])

    def item(row):
        _, kind, _, _, title, added, modified, _, url = row
        stamps = {"dateAdded": (added or 0) // 1000, "lastModified": (modified or added or 0) // 1000}
        if kind == 1 and url and url.startswith(KEEP_SCHEMES):
            return {"id": str(uuid.uuid4()), **stamps, "type": "bookmark", "url": url, "title": title or url}
        if kind == 2:
            inner = [i for i in (item(child) for child in children.get(row[0], [])) if i]
            if inner:
                return {"id": str(uuid.uuid4()), **stamps, "type": "folder", "title": title or "Folder", "children": inner}
        return None  # separators, tags, empty folders, places: queries

    items = []
    for guid, folder_name in ROOTS:
        root = by_guid.get(guid)
        if root is None:
            continue
        inner = [i for i in (item(child) for child in children.get(root[0], [])) if i]
        if not inner:
            continue
        if folder_name is None:
            items.extend(inner)
        else:
            stamps = {"dateAdded": (root[5] or 0) // 1000, "lastModified": (root[6] or 0) // 1000}
            items.append({"id": str(uuid.uuid4()), **stamps, "type": "folder", "title": folder_name, "children": inner})
    return items


def main():
    profile, out = Path(sys.argv[1]), Path(sys.argv[2])
    places = profile / "places.sqlite"
    if not places.exists() or out.exists():
        return 0
    items = convert(load(places))
    if not items:
        return 0
    out.parent.mkdir(parents=True, exist_ok=True)
    tmp = out.with_suffix(".json.new")
    tmp.write_text(json.dumps({"version": 2, "items": items}))
    tmp.replace(out)
    print(f"Brought {sum(1 for _ in walk(items))} bookmarks over from the Firefox-based browser.", file=sys.stderr)
    return 0


def walk(items):
    for i in items:
        if i["type"] == "bookmark":
            yield i
        else:
            yield from walk(i["children"])


if __name__ == "__main__":
    sys.exit(main())
