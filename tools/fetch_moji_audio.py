#!/usr/bin/env python3
"""Download article recordings next to the API fixtures.

MOJi plays https://oss.mojidict.com/article/audio/{audioId}. Files are stored at
reference/moji-api/oss/article/audio/{audioId} so a static server rooted at
reference/moji-api/oss mocks that host. audio-index.json maps each article to its file.
"""

from __future__ import annotations

import json
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
API_DIR = ROOT / "reference" / "moji-api"
ARTICLE_DIR = API_DIR / "articles"
OSS_ROOT = API_DIR / "oss"
AUDIO_DIR = OSS_ROOT / "article" / "audio"
INDEX_PATH = API_DIR / "audio-index.json"
CDN = "https://oss.mojidict.com"


def articles() -> list[dict]:
    rows = []
    for folder in sorted(p for p in ARTICLE_DIR.iterdir() if (p / "detail.json").is_file()):
        detail = json.loads((folder / "detail.json").read_text(encoding="utf-8"))
        result = detail.get("result") if isinstance(detail, dict) else None
        if not isinstance(result, dict):
            continue
        rows.append(
            {
                "objectId": result.get("objectId") or folder.name,
                "title": result.get("title") or "",
                "audioId": result.get("audioId") or "",
                "audioDuration": result.get("audioDuration") or 0,
            }
        )
    return rows


def download(audio_id: str) -> tuple[str, int]:
    dest = AUDIO_DIR / audio_id
    if dest.is_file() and dest.stat().st_size > 1000:
        return audio_id, dest.stat().st_size
    dest.parent.mkdir(parents=True, exist_ok=True)
    url = f"{CDN}/article/audio/{audio_id}"
    req = urllib.request.Request(url, headers={"User-Agent": "moji-offline-mock"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        data = resp.read()
    if not data:
        raise RuntimeError(f"empty body {audio_id}")
    dest.write_bytes(data)
    return audio_id, len(data)


def main() -> None:
    rows = articles()
    audio_ids = sorted({row["audioId"] for row in rows if row["audioId"]})
    sizes: dict[str, int] = {}
    failed: list[str] = []
    AUDIO_DIR.mkdir(parents=True, exist_ok=True)
    with ThreadPoolExecutor(max_workers=8) as pool:
        futures = {pool.submit(download, audio_id): audio_id for audio_id in audio_ids}
        done = 0
        for future in as_completed(futures):
            audio_id = futures[future]
            done += 1
            try:
                _, size = future.result()
                sizes[audio_id] = size
                print(f"[{done}/{len(audio_ids)}] {size} {audio_id}", flush=True)
            except Exception as exc:  # noqa: BLE001
                failed.append(f"{audio_id} {exc}")
                print(f"[{done}/{len(audio_ids)}] FAIL {audio_id} {exc}", flush=True)

    items = []
    missing = []
    for row in rows:
        audio_id = row["audioId"]
        if not audio_id:
            missing.append({"objectId": row["objectId"], "title": row["title"]})
            continue
        items.append(
            {
                **row,
                "ossKey": f"article/audio/{audio_id}",
                "url": f"{CDN}/article/audio/{audio_id}",
                "file": f"oss/article/audio/{audio_id}",
                "bytes": sizes.get(audio_id, 0),
            }
        )
    INDEX_PATH.write_text(
        json.dumps(
            {
                "cdn": CDN,
                "mockRoot": "reference/moji-api/oss",
                "items": items,
                "missingAudio": missing,
                "failed": failed,
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    total = sum(sizes.values())
    print(f"\n音频 {len(sizes)} 个，{total / 1024 / 1024:.1f} MB，无录音 {len(missing)} 篇，失败 {len(failed)}")
    print(f"目录 {AUDIO_DIR}")
    print(f"索引 {INDEX_PATH}")


if __name__ == "__main__":
    main()
