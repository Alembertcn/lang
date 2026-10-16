#!/usr/bin/env python3
"""Bundle the Discover-tab data + images (offline assets).

Inputs
  reference/moji-api/find/*.json            (captured by tools/fetch_moji_find.py)
  reference/moji-api/responses/readPlan.list.json

Outputs (bundled into the app)
  composeApp/src/commonMain/composeResources/files/find/feed.json
  .../find/circles/{id}_{light|dark}.png     (server circle icons)
  .../find/articles/{objectId}.{ext}         (article covers)
  .../find/plans/{planId}.jpg                (reading-plan covers)
  .../find/columns/{coverId}.png             (column covers)

Image URL conventions (verified against oss.mojidict.com):
  circle icon    https://oss.mojidict.com/{iconV2Light|iconV2Dark}
  article cover  https://oss.mojidict.com/article/images/{coverId}
  plan cover     https://oss.mojidict.com/{cover field, e.g. article/images/xxx.jpg}
  column cover   https://oss.mojidict.com/column/{coverId}
An `?x-oss-process=style/article` resize is applied to covers to keep them small.
"""

from __future__ import annotations

import json
import urllib.parse
import urllib.request
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
API_DIR = ROOT / "reference" / "moji-api"
FIND_DIR = API_DIR / "find"
OUT_DIR = ROOT / "composeApp/src/commonMain/composeResources/files/find"
CDN = "https://oss.mojidict.com"
RESIZE_COVER = "?x-oss-process=style/article"
RESIZE_THUMB = "?x-oss-process=image/resize,w_320"
# 备考 tag ids observed on the original Discover short-article rows (see REVERSE.md).
TAG_PREP_IDS = {"10000026", "10000009"}


def fetch(path: str, dest: Path, process: str = "") -> bool:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.is_file() and dest.stat().st_size > 500:
        return True
    url = f"{CDN}/{path}" + process
    req = urllib.request.Request(url, headers={"User-Agent": "moji-offline-mock"})
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = resp.read()
    except Exception as exc:  # noqa: BLE001
        print(f"  ! {path}: {exc}")
        return False
    dest.write_bytes(data)
    return True


def ext_of(cover_id: str, default: str = "png") -> str:
    suffix = Path(cover_id).suffix.lstrip(".")
    return suffix or default


def level_label(tags: list[str]) -> str:
    tags = [t for t in tags if t]
    return "~".join(reversed(tags)) if tags else ""


def length_label(chars: int) -> str:
    if chars < 1000:
        return "short"
    if chars < 2000:
        return "medium"
    return "long"


def iso_ms(value: str | None) -> int:
    if not value:
        return 0
    try:
        return int(datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp() * 1000)
    except Exception:  # noqa: BLE001
        return 0


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    circles_raw = json.loads((FIND_DIR / "circle-simpleList.json").read_text(encoding="utf-8"))
    circles = []
    for item in circles_raw["result"]["list"]:
        cid = item["objectId"]
        # iconV2* = the whole tile (avatar + baked-in circle label) exactly as the
        # original Discover row renders it.
        light, dark = item.get("iconV2Light", ""), item.get("iconV2Dark", "")
        ok_l = fetch(light, OUT_DIR / "circles" / f"{cid}_light.png") if light else False
        ok_d = fetch(dark, OUT_DIR / "circles" / f"{cid}_dark.png") if dark else False
        circles.append({
            "id": cid,
            "title": item.get("title", ""),
            "light": f"circles/{cid}_light.png" if ok_l else "",
            "dark": f"circles/{cid}_dark.png" if ok_d else "",
        })
    print(f"circles: {len(circles)}")

    recm_articles = json.loads(
        (FIND_DIR / "readingCnt-privateRecmArticles.json").read_text(encoding="utf-8")
    )["result"]["articleList"]
    by_id = {a["_id"]: a for a in recm_articles}
    homepage = json.loads((FIND_DIR / "homepage-articles.json").read_text(encoding="utf-8"))
    short_articles = []
    for item in homepage.get("recommendedArticles", []):
        oid = item["objectId"]
        meta = by_id.get(oid, {})
        cover_id = meta.get("coverId", "")
        rel = ""
        if cover_id:
            rel = f"articles/{oid}.{ext_of(cover_id, 'jpg')}"
            fetch(f"article/images/{cover_id}", OUT_DIR / rel, process=RESIZE_COVER)
        tags = meta.get("readingTags") or []
        short_articles.append({
            "id": oid,
            "title": item.get("title") or meta.get("title", ""),
            "cover": rel,
            "level": level_label(meta.get("levelTags") or []),
            "tag": "备考" if TAG_PREP_IDS & set(tags) else "",
            "length": length_label(int(meta.get("charsNum") or 0)),
        })
    print(f"shortArticles: {len(short_articles)}")

    news_raw = json.loads((FIND_DIR / "nanews.list.json").read_text(encoding="utf-8"))
    news_list = news_raw.get("list") or news_raw.get("result", {}).get("list") or []
    news = [
        {"id": n["objectId"], "title": n.get("title", ""), "topic": n.get("topic", ""), "dateMs": n.get("date", 0)}
        for n in news_list
    ]
    print(f"news: {len(news)}")

    def as_columns(column_list: list[dict], limit: int) -> list[dict]:
        out = []
        for col in column_list[:limit]:
            cover_id = col.get("coverId") or ""
            rel = ""
            if cover_id:
                name = urllib.parse.quote(cover_id, safe="")
                rel = f"columns/{name}.png"
                fetch(f"column/{cover_id}", OUT_DIR / rel, process=RESIZE_THUMB)
            articles = [
                _column_article(a) for a in (col.get("latestArticles") or [])[:2]
            ]
            out.append({
                "id": col.get("objectId") or col.get("id", ""),
                "title": col.get("title", ""),
                "subtitle": col.get("subtitle", ""),
                "cover": rel,
                "articles": articles,
            })
        return out

    def _column_article(a: dict) -> dict:
        oid = a.get("objectId") or a.get("_id", "")
        cover_id = a.get("coverId") or ""
        rel = ""
        if cover_id and oid:
            rel = f"articles/{oid}.{ext_of(cover_id, 'jpg')}"
            fetch(f"article/images/{cover_id}", OUT_DIR / rel, process=RESIZE_THUMB)
        return {
            "id": oid,
            "title": a.get("title", ""),
            "vip": bool(a.get("isVIP")),
            "cover": rel,
            "timeMs": iso_ms(a.get("publishedAt")),
        }

    recm_columns = json.loads(
        (FIND_DIR / "readingCnt-privateRecmColumns.json").read_text(encoding="utf-8")
    )["result"]["columnList"]
    columns = as_columns(recm_columns, 1000)
    print(f"columns(推荐): {len(columns)}")

    category_tabs = [("考试", "BEI_KAO"), ("时事", "SHI_SHI"), ("生活", "WEN_HUA"),
                     ("文学", "WEN_XUE"), ("娱乐", "YU_LE")]
    categories = []
    for label, tab in category_tabs:
        path = FIND_DIR / "category" / f"{tab}.json"
        if not path.is_file():
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        cols = data.get("result", data).get("columnList") or []
        categories.append({"tab": label, "columns": as_columns(cols, 1000)})
    print(f"categories: {len(categories)}")

    plan_covers_ok = 0
    plan_list_path = API_DIR / "responses" / "readPlan.list.json"
    if plan_list_path.is_file():
        payload = json.loads(plan_list_path.read_text(encoding="utf-8"))
        plans = payload.get("readPlans") or payload.get("result", {}).get("readPlans") or []
        for plan in plans:
            pid = plan.get("objectId") or plan.get("id") or ""
            cover = plan.get("cover") or ""
            if not pid or not cover:
                continue
            if fetch(cover, OUT_DIR / "plans" / f"{pid}.jpg", process=RESIZE_COVER):
                plan_covers_ok += 1
    print(f"plan covers: {plan_covers_ok}")

    clock = json.loads((FIND_DIR / "clockInDaily.json").read_text(encoding="utf-8"))
    feed = {
        # local reading-time placeholder until the stats module lands (P9)
        "readMinutes": 0,
        "checkedIn": bool(clock.get("isClockIn")),
        "circles": circles,
        "shortArticles": short_articles,
        "news": news,
        "columns": columns,
        "categories": categories,
    }
    (OUT_DIR / "feed.json").write_text(
        json.dumps(feed, ensure_ascii=False, separators=(",", ":")), encoding="utf-8"
    )
    print(f"done -> {OUT_DIR}")


if __name__ == "__main__":
    main()
