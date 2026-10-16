#!/usr/bin/env python3
"""Download reader-shelf covers into the OSS mock tree.

MOJi loads a book cover from
https://oss.mojidict.com/books/{objectId}/{coverId}
(style/bookLib is only a resize process on that object).
"""

from __future__ import annotations

import json
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
API_DIR = ROOT / "reference" / "moji-api"
SHELF = API_DIR / "reader" / "books-incFetch-v2.json"
OSS_ROOT = API_DIR / "oss"
INDEX = API_DIR / "cover-index.json"
CDN = "https://oss.mojidict.com"


def main() -> None:
    books = json.loads(SHELF.read_text(encoding="utf-8"))["result"]
    items = []
    for book in books:
        object_id = book["objectId"]
        cover_id = book["coverId"]
        key = f"books/{object_id}/{cover_id}"
        dest = OSS_ROOT / key
        dest.parent.mkdir(parents=True, exist_ok=True)
        if not dest.is_file() or dest.stat().st_size < 1000:
            req = urllib.request.Request(f"{CDN}/{key}", headers={"User-Agent": "moji-offline-mock"})
            with urllib.request.urlopen(req, timeout=60) as resp:
                data = resp.read()
                content_type = resp.headers.get("Content-Type", "")
            dest.write_bytes(data)
        else:
            data = dest.read_bytes()
            content_type = "image/jpeg"
        items.append(
            {
                "objectId": object_id,
                "title": book.get("title") or "",
                "coverId": cover_id,
                "ossKey": key,
                "url": f"{CDN}/{key}",
                "file": f"oss/{key}",
                "bytes": len(data) if dest.is_file() else dest.stat().st_size,
                "contentType": content_type,
            }
        )
        print(f"{items[-1]['bytes']} {book.get('title')} {key}", flush=True)
    INDEX.write_text(json.dumps({"cdn": CDN, "mockRoot": "reference/moji-api/oss", "items": items}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"\n封面 {len(items)} 张，索引 {INDEX}")


if __name__ == "__main__":
    main()
