#!/usr/bin/env python3
"""Pull the read-only APIs the MOJi Find tab loads into reference/moji-api/find.

Skips writes: readingFav-commit, clock-in sync/catch-up, analytics.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import fetch_moji_articles as articles  # noqa: E402
import fetch_moji_plans as moji  # noqa: E402

FIND_DIR = moji.API_DIR / "find"
# FindFragment category tabs other than 全部. 全部 uses privateRecmColumns.
CATEGORY_TABS = ("BEI_KAO", "SHI_SHI", "WEN_HUA", "WEN_XUE", "YU_LE")


def write_json(path: Path, payload) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def as_list(value) -> list:
    return value if isinstance(value, list) else []


def favor_lists(payload) -> tuple[list, list, list]:
    data = payload.get("result") if isinstance(payload, dict) and isinstance(payload.get("result"), dict) else payload
    if not isinstance(data, dict):
        data = {}
    return as_list(data.get("levels")), as_list(data.get("expectIncres")), as_list(data.get("expectKnow"))


def rest_get(token: str, path: str, query: dict | None, dest: Path) -> str:
    status, body = moji.request("GET", path, token, query=query)
    if status != 200 or not isinstance(body, dict):
        write_json(dest.with_suffix(".error.json"), {"status": status, "body": body})
        return f"FAIL {path} HTTP {status}"
    write_json(dest, body)
    return f"ok {dest.relative_to(moji.API_DIR)}"


def main() -> None:
    env = moji.load_env(moji.ENV_PATH)
    account = env.get("MOJI_ACCOUNT", "")
    password = env.get("MOJI_PASSWORD", "")
    if not account or not password:
        sys.exit("请在 .env 填写 MOJI_ACCOUNT 和 MOJI_PASSWORD。")
    token = moji.login(account, password)

    # 偏好只用来填推荐参数，响应本身不落盘。
    levels, expect_incres, expect_know = favor_lists(articles.cloud("readingFav-fetch", token, {}))
    rec_params = {"levels": levels, "expectIncres": expect_incres, "expectKnow": expect_know}
    jobs = [
        ("readingCnt-privateRecmArticles", rec_params, FIND_DIR / "readingCnt-privateRecmArticles.json"),
        ("readingCnt-privateRecmColumns", rec_params, FIND_DIR / "readingCnt-privateRecmColumns.json"),
        ("circle-simpleList", {}, FIND_DIR / "circle-simpleList.json"),
    ]

    ok = 0
    failed = 0
    for name, params, path in jobs:
        try:
            write_json(path, articles.cloud(name, token, params))
            print(f"ok {path.relative_to(moji.API_DIR)}", flush=True)
            ok += 1
        except Exception as exc:  # noqa: BLE001
            write_json(path.with_suffix(".error.json"), {"error": str(exc), "params": params})
            print(f"FAIL {name} {exc}", flush=True)
            failed += 1

    for tab in CATEGORY_TABS:
        path = FIND_DIR / "category" / f"{tab}.json"
        params = {
            "listType": "COLUMN_LIST",
            "levelTag": ["ALL"],
            "categories": [tab],
            "payType": "ALL",
            "type": "ALL",
            "page": 1,
            "sort": {"publishedAt": -1, "subscribedNum": 0},
        }
        try:
            write_json(path, articles.cloud("readingCnt-category", token, params))
            print(f"ok {path.relative_to(moji.API_DIR)}", flush=True)
            ok += 1
        except Exception as exc:  # noqa: BLE001
            write_json(path.with_suffix(".error.json"), {"error": str(exc), "params": params})
            print(f"FAIL readingCnt-category {tab} {exc}", flush=True)
            failed += 1

    for line in (
        rest_get(token, "/v1/nanews/list", {"page": 1, "limit": 20}, FIND_DIR / "nanews.list.json"),
        rest_get(token, "/v1/clockInDaily", None, FIND_DIR / "clockInDaily.json"),
    ):
        print(line, flush=True)
        if line.startswith("ok"):
            ok += 1
        else:
            failed += 1
    print(f"\n发现页 {ok} 个成功，{failed} 个失败")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
