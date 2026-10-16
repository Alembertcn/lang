#!/usr/bin/env python3
"""Download article bodies for the Find homepage lists already saved under find/.

Reading articles use the same reading-article-detail + zip HTML flow as plan articles.
News uses GET /v1/nanews/{id}?isNlt=true, then the public zip
https://oss.mojidict.com/nanews/{id}-notation-{level}.zip.
"""

from __future__ import annotations

import json
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import fetch_moji_articles as articles  # noqa: E402
import fetch_moji_plans as moji  # noqa: E402

FIND_DIR = moji.API_DIR / "find"
NEWS_DIR = FIND_DIR / "news"
CDN = "https://oss.mojidict.com"
LEVELS = ("n3n4n5", "n2", "n1")


def write_json(path: Path, payload) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def column_lists() -> list[tuple[str, dict]]:
    rows = []
    payload = load(FIND_DIR / "readingCnt-privateRecmColumns.json")
    for column in payload["result"]["columnList"]:
        rows.append(("recommendedColumns", column))
    for path in sorted((FIND_DIR / "category").glob("*.json")):
        inner = load(path)
        inner = inner.get("result", inner)
        for column in inner.get("columnList") or []:
            rows.append((path.stem, column))
    return rows


def reading_targets() -> dict[str, str]:
    found: dict[str, str] = {}
    payload = load(FIND_DIR / "readingCnt-privateRecmArticles.json")
    for article in payload["result"]["articleList"]:
        object_id = article.get("objectId")
        if object_id:
            found[object_id] = article.get("title") or object_id
    for _source, column in column_lists():
        for article in column.get("latestArticles") or []:
            object_id = article.get("objectId")
            if object_id and object_id not in found:
                found[object_id] = article.get("title") or object_id
    return found


def article_ref(article: dict) -> dict:
    object_id = article.get("objectId")
    return {
        "objectId": object_id,
        "title": article.get("title") or "",
        "file": f"articles/{object_id}",
    }


def build_index() -> dict:
    recommended = [
        article_ref(article)
        for article in load(FIND_DIR / "readingCnt-privateRecmArticles.json")["result"]["articleList"]
        if article.get("objectId")
    ]
    columns = []
    categories: dict[str, list] = {}
    for source, column in column_lists():
        entry = {
            "objectId": column.get("objectId"),
            "title": column.get("title") or "",
            "articles": [article_ref(article) for article in column.get("latestArticles") or [] if article.get("objectId")],
        }
        if source == "recommendedColumns":
            columns.append(entry)
        else:
            categories.setdefault(source, []).append(entry)
    news = []
    for item in load(FIND_DIR / "nanews.list.json")["list"]:
        object_id = item.get("objectId")
        if object_id:
            news.append(
                {
                    "objectId": object_id,
                    "title": item.get("title") or "",
                    "file": f"find/news/{object_id}",
                }
            )
    return {
        "recommendedArticles": recommended,
        "recommendedColumns": columns,
        "categories": categories,
        "news": news,
    }


def pick_level(levels: list | None) -> str:
    have = set(levels or [])
    for level in LEVELS:
        if level in have:
            return level
    if levels:
        return levels[0]
    return LEVELS[0]


def fetch_news(token: str, object_id: str, title: str) -> str:
    folder = NEWS_DIR / object_id
    folder.mkdir(parents=True, exist_ok=True)
    detail = None
    for _ in range(8):
        status, detail = moji.request("GET", f"/v1/nanews/{object_id}", token, query={"isNlt": "true"})
        if status != 200 or not isinstance(detail, dict):
            raise RuntimeError(f"{object_id} HTTP {status}")
        if detail.get("notationHtmlLevels") and not detail.get("isPolling"):
            break
        time.sleep(2)
    write_json(folder / "detail.json", detail)
    level = pick_level(detail.get("notationHtmlLevels") if isinstance(detail, dict) else None)
    key = f"nanews/{object_id}-notation-{level}.zip"
    req = urllib.request.Request(f"{CDN}/{key}", headers={"User-Agent": "moji-offline-mock"})
    blob = b""
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=40) as resp:
                blob = resp.read()
            break
        except urllib.error.HTTPError as exc:
            if exc.code != 404 or attempt == 3:
                raise
            time.sleep(2)
    (folder / "body.html").write_text(articles.html_from_zip(blob), encoding="utf-8")
    (folder / "body-source.json").write_text(
        json.dumps({"level": level, "ossKey": key, "url": f"{CDN}/{key}"}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return f"{object_id} {level} {title}"


def main() -> None:
    env = moji.load_env(moji.ENV_PATH)
    account = env.get("MOJI_ACCOUNT", "")
    password = env.get("MOJI_PASSWORD", "")
    if not account or not password:
        sys.exit("请在 .env 填写 MOJI_ACCOUNT 和 MOJI_PASSWORD。")
    token = moji.login(account, password)
    targets = reading_targets()
    pending = [
        (object_id, title)
        for object_id, title in targets.items()
        if not (articles.ARTICLE_DIR / object_id / "body.html").is_file()
    ]
    print(f"阅读文章 {len(targets)}，待拉取 {len(pending)}", flush=True)
    failed = 0
    with ThreadPoolExecutor(max_workers=6) as pool:
        futures = [pool.submit(articles.fetch_one, token, object_id, title) for object_id, title in pending]
        for future in as_completed(futures):
            try:
                print(future.result(), flush=True)
            except Exception as exc:  # noqa: BLE001
                failed += 1
                print(f"FAIL article {exc}", flush=True)
    news_items = [
        (item["objectId"], item.get("title") or item["objectId"])
        for item in load(FIND_DIR / "nanews.list.json")["list"]
        if item.get("objectId") and not (NEWS_DIR / item["objectId"] / "body.html").is_file()
    ]
    print(f"新闻待拉取 {len(news_items)}", flush=True)
    with ThreadPoolExecutor(max_workers=4) as pool:
        futures = [pool.submit(fetch_news, token, object_id, title) for object_id, title in news_items]
        for future in as_completed(futures):
            try:
                print(future.result(), flush=True)
            except Exception as exc:  # noqa: BLE001
                failed += 1
                print(f"FAIL news {exc}", flush=True)
    write_json(FIND_DIR / "homepage-articles.json", build_index())
    print(f"索引 find/homepage-articles.json，失败 {failed}", flush=True)
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
