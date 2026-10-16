#!/usr/bin/env python3
"""Unpack MOJi book epubs into the app's composeResources — original-path pipeline.

Input : reference/moji-api/oss/books/{objectId}/{contentId}.zip   (outer zip -> epub)
Output: composeApp/src/commonMain/composeResources/files/books/{objectId}/...  (full epub tree)

This replaces the old `convert_moji_books.py`. The OSS epubs are already reader-ready:
every chapter references `index.css` plus the reader engine scripts
(jquery / readium-cfi / rangy / web-highlighter / Bridge.js, ...) and carries
`moji-id` attributes for word/paragraph identity. The engine files are not inside
the epub; the in-app local HTTP server supplies them (see content/engine/INTEGRATION.md).

So here we keep **every** file byte-for-byte (chapters, CSS, images, fonts, OPF,
NCX, container.xml). No rewriting, no filtering.
"""

from __future__ import annotations

import io
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
API_DIR = ROOT / "reference" / "moji-api"
BOOKS_JSON = API_DIR / "reader" / "books-incFetch-v2.json"
OSS_BOOKS = API_DIR / "oss" / "books"
OUT_DIR = ROOT / "composeApp/src/commonMain/composeResources/files/books"


def safe_members(epub: zipfile.ZipFile) -> list[zipfile.ZipInfo]:
    out: list[zipfile.ZipInfo] = []
    for info in epub.infolist():
        name = info.filename
        if name.startswith("/") or ".." in Path(name).parts:
            print(f"  ! skip unsafe entry: {name}")
            continue
        if info.is_dir():
            continue
        out.append(info)
    return out


def unpack_book(object_id: str, title: str, content_id: str) -> int | None:
    outer_path = OSS_BOOKS / object_id / f"{content_id}.zip"
    if not outer_path.exists():
        return None
    with zipfile.ZipFile(outer_path) as outer:
        epub_name = next((n for n in outer.namelist() if n.lower().endswith(".epub")), None)
        if not epub_name:
            return None
        epub_bytes = outer.read(epub_name)

    target_root = OUT_DIR / object_id
    count = 0
    with zipfile.ZipFile(io.BytesIO(epub_bytes)) as epub:
        for info in safe_members(epub):
            dest = target_root / info.filename
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(epub.read(info))
            count += 1
    print(f"ok    {object_id}  files={count:>3}  {title}")
    return count


def main() -> None:
    if not BOOKS_JSON.exists():
        sys.exit(f"missing {BOOKS_JSON} — run tools/fetch_moji_books.py first")
    data = json.loads(BOOKS_JSON.read_text(encoding="utf-8"))
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    ok = 0
    total = 0
    for book in data.get("result", []):
        object_id = book.get("objectId") or ""
        content_id = book.get("contentId") or ""
        title = book.get("title") or object_id
        if not object_id or not content_id:
            continue
        files = unpack_book(object_id, title, content_id)
        if files is None:
            print(f"skip  {object_id}  (source zip missing — run tools/fetch_moji_books.py)")
            continue
        ok += 1
        total += files
    print(f"done: {ok} books, {total} files -> {OUT_DIR}")


if __name__ == "__main__":
    main()
