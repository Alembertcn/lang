#!/usr/bin/env python3
"""Download the official bookshelf epubs.

Each book's package is at  https://oss.mojidict.com/books/{objectId}/{contentId}.zip
(an outer zip whose single entry is the real .epub). Books come from
reference/moji-api/reader/books-incFetch-v2.json; files land in the mock OSS tree
so the same tree can serve them over the local media mock.
"""

from __future__ import annotations

import json
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
API_DIR = ROOT / "reference" / "moji-api"
BOOKS_JSON = API_DIR / "reader" / "books-incFetch-v2.json"
OSS_BOOKS = API_DIR / "oss" / "books"
CDN = "https://oss.mojidict.com"


def main() -> None:
    data = json.loads(BOOKS_JSON.read_text(encoding="utf-8"))
    books = data.get("result", [])
    done = 0
    for book in books:
        object_id = book.get("objectId") or ""
        content_id = book.get("contentId") or ""
        title = book.get("title") or ""
        if not object_id or not content_id:
            continue
        target = OSS_BOOKS / object_id / f"{content_id}.zip"
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists() and target.stat().st_size > 0:
            print(f"skip  {object_id}  {title}")
            done += 1
            continue
        url = f"{CDN}/books/{object_id}/{content_id}.zip"
        with urllib.request.urlopen(url, timeout=90) as resp:
            payload = resp.read()
        target.write_bytes(payload)
        print(f"ok    {object_id}  {len(payload):>7} bytes  {title}")
        done += 1
    print(f"done: {done}/{len(books)}")


if __name__ == "__main__":
    main()
