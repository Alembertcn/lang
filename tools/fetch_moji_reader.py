#!/usr/bin/env python3
"""Pull read-only MOJi reader APIs into reference/moji-api for local mocks.

Skips writes: subscribe, bookmark insert/delete, progress set, history clean,
book upload/delete, voice notify.
"""

from __future__ import annotations

import json
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import fetch_moji_articles as articles  # noqa: E402
import fetch_moji_plans as moji  # noqa: E402

API_DIR = moji.API_DIR
READER_DIR = API_DIR / "reader"
ARTICLE_DIR = API_DIR / "articles"


def write_json(path: Path, payload) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def call(token: str, name: str, params: dict, path: Path) -> str:
    try:
        payload = articles.cloud(name, token, params)
        write_json(path, payload)
        return f"ok {path.relative_to(API_DIR)}"
    except Exception as exc:  # noqa: BLE001
        write_json(path.with_suffix(".error.json"), {"error": str(exc), "params": params})
        return f"FAIL {name} {exc}"


def column_ids() -> list[str]:
    found: set[str] = set()
    for folder in ARTICLE_DIR.iterdir():
        detail_path = folder / "detail.json"
        if not detail_path.is_file():
            continue
        detail = json.loads(detail_path.read_text(encoding="utf-8"))
        result = detail.get("result") if isinstance(detail, dict) else None
        column_id = result.get("columnId") if isinstance(result, dict) else None
        if column_id:
            found.add(column_id)
    return sorted(found)


def article_ids() -> list[str]:
    return sorted(
        folder.name
        for folder in ARTICLE_DIR.iterdir()
        if (folder / "detail.json").is_file()
    )


def main() -> None:
    env = moji.load_env(moji.ENV_PATH)
    account = env.get("MOJI_ACCOUNT", "")
    password = env.get("MOJI_PASSWORD", "")
    if not account or not password:
        sys.exit("请在 .env 填写 MOJI_ACCOUNT 和 MOJI_PASSWORD。")
    token = moji.login(account, password)
    now_ms = int(time.time() * 1000)
    jobs: list[tuple[str, dict, Path]] = [
        ("tts-voice-actors-fetch", {"lang": "ja"}, READER_DIR / "tts-voice-actors-fetch.json"),
        ("bookLibs-hot", {}, READER_DIR / "bookLibs-hot.json"),
        ("books-incFetch-v2", {"lfd": 0}, READER_DIR / "books-incFetch-v2.json"),
        ("column-mySubscribed", {}, READER_DIR / "column-mySubscribed.json"),
        (
            "reading-histories",
            {"ltAt": now_ms, "type": "Article", "limit": 20},
            READER_DIR / "reading-histories.json",
        ),
        (
            "reading-column-list",
            {"page": 1, "limit": 50},
            READER_DIR / "reading-column-list.json",
        ),
        (
            "reading-article-everyDayOne",
            {"curTime": now_ms},
            READER_DIR / "reading-article-everyDayOne.json",
        ),
    ]
    for column_id in column_ids():
        base = READER_DIR / "columns" / column_id
        jobs.append(("reading-column-directory", {"objectId": column_id}, base / "directory.json"))
        jobs.append(
            (
                "reading-column-content",
                {"objectId": column_id, "page": 1, "limit": 50},
                base / "content.json",
            )
        )
        jobs.append(
            (
                "reading-column-audioList",
                {"objectId": column_id, "limit": 20, "publishedAtTime": 0, "publishedAtTimeSort": -1},
                base / "audioList.json",
            )
        )
    for article_id in article_ids():
        jobs.append(
            (
                "article-voice-list",
                {"articleId": article_id},
                ARTICLE_DIR / article_id / "voice-list.json",
            )
        )

    ok = 0
    failed = 0
    with ThreadPoolExecutor(max_workers=6) as pool:
        futures = [pool.submit(call, token, name, params, path) for name, params, path in jobs]
        for index, future in enumerate(as_completed(futures), start=1):
            line = future.result()
            if line.startswith("FAIL"):
                failed += 1
            else:
                ok += 1
            print(f"[{index}/{len(jobs)}] {line}", flush=True)
    print(f"\n完成 {ok}，失败 {failed}，目录 {READER_DIR}")


if __name__ == "__main__":
    main()
