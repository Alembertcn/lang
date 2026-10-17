#!/usr/bin/env python3
"""Bundle the Study tab (学习) data from the captured API cache.

Sources (reference/moji-api)
  reader/readStats-articleMonthlyStats.json   -> 本月学习 X 天 / Y 小时
  responses/readPlan.mine.json                -> 我的计划 + 进度
  responses/readPlan.detail/{planId}.json     -> 计划章节 / 文章标题
  reader/column-mySubscribed.json             -> 订阅（本次账号为空）

Output (composeResources, gitignored — regenerate with this script)
  files/study/study.json

Plan covers already exist under files/find/plans/{planId}.jpg and column covers
under files/find/columns/{coverId}.png (fetch_find_assets.py), so the Study UI
reuses them instead of duplicating bytes.
"""

from __future__ import annotations

import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
API = ROOT / "reference" / "moji-api"
RES = ROOT / "composeApp" / "src" / "commonMain" / "composeResources" / "files"
OUT = RES / "study" / "study.json"


def read(path: Path, default):
    if not path.exists():
        return default
    return json.loads(path.read_text(encoding="utf-8"))


def result_of(payload):
    if isinstance(payload, dict) and isinstance(payload.get("result"), dict):
        return payload["result"]
    return payload if isinstance(payload, dict) else {}


def plan_cover(plan_id: str) -> str:
    path = RES / "find" / "plans" / f"{plan_id}.jpg"
    return f"plans/{plan_id}.jpg" if path.exists() else ""


def my_plans() -> list[dict]:
    mine = read(API / "responses" / "readPlan.mine.json", {})
    plans = {p.get("objectId"): p for p in mine.get("readPlans", [])}
    rows = []
    for progress in mine.get("userReadPlans", []):
        plan_id = progress.get("planId", "")
        plan = plans.get(plan_id, {})
        detail = result_of(read(API / "responses" / "readPlan.detail" / f"{plan_id}.json", {}))
        detail_plan = detail.get("readPlan") or {}
        articles = {a.get("objectId"): a.get("title", "") for a in detail.get("articles", [])}
        done_ids = set(progress.get("doneArtIds") or [])
        for a in progress.get("readArticles") or []:
            if a.get("id"):
                done_ids.add(a["id"])
        art_num = plan.get("artNum") or detail_plan.get("artNum") or 0
        chapters = []
        for chapter in detail_plan.get("chapters") or plan.get("chapters") or []:
            chapters.append(
                {
                    "title": chapter.get("title", ""),
                    "articles": [
                        {"id": aid, "title": articles.get(aid, ""), "done": aid in done_ids}
                        for aid in chapter.get("artIds") or []
                    ],
                }
            )
        per_day = max(1, int(progress.get("dailyReading") or 1))
        remaining = max(0, art_num - len(done_ids))
        rows.append(
            {
                "id": plan_id,
                "title": plan.get("title") or detail_plan.get("title", ""),
                "coverFile": plan_cover(plan_id),
                "artNum": art_num,
                "done": len(done_ids),
                "daysLeft": max(1, math.ceil(remaining / per_day)) if remaining else 0,
                "chapters": chapters,
            }
        )
    return rows


def main() -> None:
    stats = result_of(read(API / "reader" / "readStats-articleMonthlyStats.json", {}))
    subscribed = read(API / "reader" / "column-mySubscribed.json", {})
    subscribed_rows = result_of(subscribed).get("data") or []
    payload = {
        "stats": {
            "days": int(stats.get("totalDays") or 0),
            "durSec": int(stats.get("totalDurSec") or 0),
            "num": int(stats.get("totalNum") or 0),
        },
        "plans": my_plans(),
        "subscribed": [
            {
                "id": c.get("objectId", ""),
                "title": c.get("title", ""),
                "subtitle": c.get("subtitle", ""),
                "articleNum": c.get("articleNum") or 0,
                "coverId": c.get("coverId", ""),
            }
            for c in subscribed_rows
        ],
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(
        f"study.json: {len(payload['plans'])} plans, "
        f"{len(payload['subscribed'])} subscriptions, "
        f"{payload['stats']['days']} study days -> {OUT.relative_to(ROOT)}"
    )


if __name__ == "__main__":
    main()
