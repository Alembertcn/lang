#!/usr/bin/env python3
"""Bundle the original MOJi article H5 + article bodies as app resources.

Source of truth (never rewritten):
  content/h5/web/mojiread/article/android/index.html      original article page
  content/h5/h5hybrid/assets/*                            original Vue bundles/CSS/icons
  reference/moji-read/base.apk  assets/fonts/...          Noto Sans JP regular
  reference/moji-api/articles/{id}/body.html              article body, copied 1:1
  reference/moji-api/articles/{id}/detail.json            metadata (title/cover/audio/...)
  reference/moji-api/find/homepage-articles.json          which articles the Discover tab links to

Outputs (composeResources, gitignored — regenerate with this script):
  files/article_h5/index.html                 page shell (loads assets by absolute /android_asset/...)
  files/article_h5/assets/*                   original JS/CSS/PNG bundles
  files/article_h5/assets/local-fonts/...     font at the exact path the CSS requests
  files/article_detail/{id}.json              metadata the native shell needs
  files/article_detail/{id}.html              body, byte-identical to the captured body.html

The article page is the original H5 path: the WebView loads index.html from the
loopback ArticleServer and the native side calls MOJiArticleDetailDidSetHTML()
with the captured body, exactly like ArticleDetailFragment does.
"""

from __future__ import annotations

import json
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
API = ROOT / "reference" / "moji-api"
APK = ROOT / "reference" / "moji-read" / "base.apk"
RES = ROOT / "composeApp" / "src" / "commonMain" / "composeResources" / "files"
H5_OUT = RES / "article_h5"
DETAIL_OUT = RES / "article_detail"

H5_SRC = ROOT / "content" / "h5" / "web" / "mojiread" / "article" / "android" / "index.html"
ASSETS_SRC = ROOT / "content" / "h5" / "h5hybrid" / "assets"

# Everything index.html pulls in, directly or through the Vue chunk graph.
H5_ASSETS = (
    "jquery-0d70e8d2.js",
    "web-highlighter-bf5f15dd.js",
    "@floating-ui-2273cd8d.js",
    "nativeUtils-99cd14c9.js",
    "MOJiArticleDetail-2bef2f4b.js",
    "index-fd51caf4.js",
    "MOJiArticleDetail-c85e7cf7.css",
    "index-8b858b34.css",
    "ic_article_ai_bright-f6825ff2.png",
    "ic_article_ai_dark-7410c5d4.png",
    "ic_article_auxiliary_bright-f4f91aaf.png",
    "ic_article_auxiliary_dark-b348182c.png",
    "ic_article_bright-cfc2c4c0.png",
    "ic_article_dark-f520ae0f.png",
    "ic_article_note_bright-081c5cfb.png",
    "ic_article_note_dark-bde18638.png",
    "player-pause-35c73064.png",
    "player-start-b37ca006.png",
)

FONT = "noto_sans_jp_regular.otf"
# The CSS asks for url(local-fonts/android_asset/fonts/<font>) relative to
# /android_asset/h5hybrid/assets/, so serve it from that exact nested path.
FONT_OUT = H5_OUT / "assets" / "local-fonts" / "android_asset" / "fonts" / FONT


def fail(msg: str) -> None:
    sys.exit(f"build_article_assets: {msg}")


def article_ids() -> list[str]:
    """Every captured article: plan lessons (205) + Discover extras (94).

    The same detail page is reached from both entry points in the original app.
    """
    return sorted(p.name for p in (API / "articles").iterdir() if (p / "body.html").exists())


def iso_date(value) -> str:
    if isinstance(value, dict):
        text = value.get("iso") or ""
    else:
        text = str(value or "")
    return text[:10].replace("-", "/")


def cover_file(article_id: str) -> str:
    """Cover already bundled for the Discover tab (files/find/articles/{id}.*)."""
    folder = RES / "find" / "articles"
    for p in folder.glob(f"{article_id}.*"):
        return p.name
    return ""


def article_meta(article_id: str) -> dict:
    detail = json.loads((API / "articles" / article_id / "detail.json").read_text(encoding="utf-8"))
    r = detail.get("result", detail)
    column = r.get("column") or {}
    author_id = r.get("createdBy", "")
    author_name = ""
    # The captured detail.json is the Parse "include" response: related users
    # and the column arrive as top-level numeric keys ("1", "211", ...).
    for value in detail.values():
        if not isinstance(value, list) or not value or not isinstance(value[0], dict):
            continue
        if author_id and value[0].get("objectId") == author_id and value[0].get("name"):
            author_name = value[0]["name"]
            break
    return {
        "id": article_id,
        "title": r.get("title", ""),
        "authorId": author_id,
        "authorName": author_name,
        "columnId": r.get("columnId", ""),
        "columnTitle": column.get("title", ""),
        "coverId": r.get("coverId", ""),
        "coverFile": cover_file(article_id),
        "audioId": r.get("audioId", ""),
        "audioDuration": r.get("audioDuration", 0) or 0,
        "publishedAt": iso_date(r.get("publishedAt")),
        "updatedAt": iso_date(r.get("contentUpdatedAt") or r.get("updatedAt")),
        "previousId": r.get("previousId", "") or "",
        "nextId": r.get("nextId", "") or "",
        "transHideType": r.get("transHideType", "0") or "0",
        "levelTags": r.get("levelTags", []),
        "isVIP": bool(r.get("isVIP", False)),
    }


def main() -> None:
    missing = [n for n in (H5_SRC, ASSETS_SRC, APK) if not n.exists()]
    if missing:
        fail("missing sources: " + ", ".join(str(p) for p in missing))

    if H5_OUT.exists():
        shutil.rmtree(H5_OUT)
    if DETAIL_OUT.exists():
        shutil.rmtree(DETAIL_OUT)
    (H5_OUT / "assets").mkdir(parents=True)
    DETAIL_OUT.mkdir(parents=True)

    shutil.copy2(H5_SRC, H5_OUT / "index.html")
    for name in H5_ASSETS:
        src = ASSETS_SRC / name
        if not src.exists():
            fail(f"missing H5 asset: {src}")
        shutil.copy2(src, H5_OUT / "assets" / name)

    FONT_OUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(APK) as apk:
        try:
            data = apk.read(f"assets/fonts/{FONT}")
        except KeyError:
            fail(f"font assets/fonts/{FONT} not in {APK.name}")
    FONT_OUT.write_bytes(data)

    ids = article_ids()
    written = 0
    for article_id in ids:
        folder = API / "articles" / article_id
        body = folder / "body.html"
        detail = folder / "detail.json"
        if not body.exists() or not detail.exists():
            print(f"  skip {article_id}: body/detail missing")
            continue
        shutil.copy2(body, DETAIL_OUT / f"{article_id}.html")
        meta = article_meta(article_id)
        (DETAIL_OUT / f"{article_id}.json").write_text(
            json.dumps(meta, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        written += 1

    h5_bytes = sum(p.stat().st_size for p in H5_OUT.rglob("*") if p.is_file())
    detail_bytes = sum(p.stat().st_size for p in DETAIL_OUT.rglob("*") if p.is_file())
    print(f"H5 bundle      : {h5_bytes / 1048576:.2f} MB -> {H5_OUT}")
    print(f"article detail : {written} articles, {detail_bytes / 1048576:.2f} MB -> {DETAIL_OUT}")


if __name__ == "__main__":
    main()
