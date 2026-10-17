#!/usr/bin/env python3
"""Mirror every article image into the local OSS mock tree.

The original article page embeds absolute URLs:
  https://oss.mojidict.com/article/images/{key}[?x-oss-process=...]
  https://oss.mojidict.com/article/images/{coverId}   (article cover)

Downloading them under reference/moji-api/oss/article/images/{key} lets the
app rewrite the origin to MOCK_MEDIA_BASE at runtime, so no request ever leaves
the loopback mock (see AGENTS.md「接口一律走本地 mock」).
"""

from __future__ import annotations

import json
import re
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
API = ROOT / "reference" / "moji-api"
ARTICLES = API / "articles"
OUT = API / "oss" / "article" / "images"
CDN = "https://oss.mojidict.com"

IMAGE_RE = re.compile(r"https://oss\.mojidict\.com/(article/images/[^\"'\s)>]+)")


def keys() -> set[str]:
    found: set[str] = set()
    for body in ARTICLES.glob("*/body.html"):
        text = body.read_text(encoding="utf-8", errors="ignore")
        for match in IMAGE_RE.finditer(text):
            path = match.group(1).split("?")[0]
            found.add(path.removeprefix("article/images/"))
    for detail in ARTICLES.glob("*/detail.json"):
        payload = json.loads(detail.read_text(encoding="utf-8"))
        result = payload.get("result") if isinstance(payload, dict) else None
        cover = (result or {}).get("coverId")
        if cover:
            found.add(cover)
    return found


def fetch(key: str, style: str | None) -> str:
    dest = OUT / key
    if dest.exists() and dest.stat().st_size > 0:
        return "skip"
    url = f"{CDN}/article/images/{urllib.parse.quote(key)}"
    if style:
        url += f"?x-oss-process=style/{style}"
    req = urllib.request.Request(url, headers={"User-Agent": "moji-offline-mock"})
    with urllib.request.urlopen(req, timeout=40) as resp:
        blob = resp.read()
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(blob)
    return f"{len(blob)}"


def main() -> None:
    all_keys = sorted(keys())
    OUT.mkdir(parents=True, exist_ok=True)
    ok = 0
    failed: list[str] = []
    with ThreadPoolExecutor(max_workers=8) as pool:
        futures = {
            pool.submit(fetch, key, "article-content"): key
            for key in all_keys
        }
        for future in as_completed(futures):
            key = futures[future]
            try:
                future.result()
                ok += 1
            except Exception as exc:  # noqa: BLE001
                failed.append(f"{key} {exc}")
    total = sum(p.stat().st_size for p in OUT.glob("*") if p.is_file())
    print(f"article images: {ok}/{len(all_keys)} ok, {total / 1048576:.1f} MB -> {OUT.relative_to(ROOT)}")
    if failed:
        print("failed:", *failed[:10], sep="\n  ")


if __name__ == "__main__":
    main()
