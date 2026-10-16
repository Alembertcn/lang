#!/usr/bin/env python3
"""Download MOJi article detail + HTML body for every article in the saved plan list.

  POST {PARSE}/functions/reading-article-detail   {objectId, isNlt}
  POST {PARSE}/functions/fetchTargetSrcUrl_v2     {targetType:210, targetId, tag?}
  GET  the returned URL (a zip whose html entry is the article body)
  POST {PARSE}/functions/fav-listSens             {srcId}

Writes reference/moji-api/articles/{objectId}/detail.json, body.html, sentences.json.
"""

from __future__ import annotations

import io
import json
import sys
import urllib.request
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import fetch_moji_plans as moji  # noqa: E402

PARSE = "https://api.mojidict.com/app/mojiread/parse"
ARTICLE_DIR = moji.API_DIR / "articles"
TYPE_ARTICLE = 210


def cloud(name: str, token: str, params: dict):
    body = {"g_os": "Android", "g_ver": "v3.4.9", "g_channel": "moji", **params}
    data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(
        f"{PARSE}/functions/{name}",
        data=data,
        headers=moji.headers(token),
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=40) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    result = payload.get("result", payload) if isinstance(payload, dict) else payload
    code = result.get("code") if isinstance(result, dict) else None
    if code not in (None, 200, 0):
        raise RuntimeError(f"{name} code={code}")
    return result


def article_ids() -> list[tuple[str, str]]:
    listed = json.loads((moji.RESPONSE_DIR / "readPlan.list.json").read_text(encoding="utf-8"))
    seen: dict[str, str] = {}
    for article in listed.get("articles") or []:
        object_id = article.get("objectId")
        if object_id and object_id not in seen:
            seen[object_id] = article.get("title") or object_id
    return list(seen.items())


def html_from_zip(blob: bytes) -> str:
    with zipfile.ZipFile(io.BytesIO(blob)) as archive:
        names = [name for name in archive.namelist() if name.lower().endswith(".html") or "html" in name.lower()]
        if not names:
            names = archive.namelist()[:1]
        if not names:
            raise RuntimeError("zip 里没有文件")
        return archive.read(names[0]).decode("utf-8")


def download_body(url: str) -> str:
    req = urllib.request.Request(url, method="GET")
    with urllib.request.urlopen(req, timeout=40) as resp:
        blob = resp.read()
    if blob[:2] == b"PK":
        return html_from_zip(blob)
    return blob.decode("utf-8")


def fetch_one(token: str, object_id: str, title: str) -> str:
    folder = ARTICLE_DIR / object_id
    folder.mkdir(parents=True, exist_ok=True)
    detail = cloud("reading-article-detail", token, {"objectId": object_id, "isNlt": True})
    (folder / "detail.json").write_text(json.dumps(detail, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    result = detail.get("result") if isinstance(detail, dict) else None
    notation = bool(result.get("notationHtmlSuccess")) if isinstance(result, dict) else False
    src_params = {"targetType": TYPE_ARTICLE, "targetId": object_id}
    if notation:
        src_params["tag"] = "notation"
    src = cloud("fetchTargetSrcUrl_v2", token, src_params)
    url = src.get("result") if isinstance(src, dict) else None
    if isinstance(url, str) and url.startswith("http"):
        (folder / "body.html").write_text(download_body(url), encoding="utf-8")
        body_state = "html"
    else:
        body_state = "no-url"
    try:
        sentences = cloud("fav-listSens", token, {"srcId": object_id})
        (folder / "sentences.json").write_text(
            json.dumps(sentences, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    except Exception as exc:  # noqa: BLE001
        (folder / "sentences.error.txt").write_text(str(exc), encoding="utf-8")
        body_state += " no-sentences"
    return f"{object_id} {body_state} {title}"


def main() -> None:
    limit = int(sys.argv[1]) if len(sys.argv) > 1 else 0
    env = moji.load_env(moji.ENV_PATH)
    account = env.get("MOJI_ACCOUNT", "")
    password = env.get("MOJI_PASSWORD", "")
    if not account or not password:
        sys.exit("请在 .env 填写 MOJI_ACCOUNT 和 MOJI_PASSWORD。")
    token = moji.login(account, password)
    items = article_ids()
    if limit:
        items = items[:limit]
    ARTICLE_DIR.mkdir(parents=True, exist_ok=True)
    ok = 0
    failed: list[str] = []
    for index, (object_id, title) in enumerate(items, start=1):
        try:
            line = fetch_one(token, object_id, title)
            ok += 1
            print(f"[{index}/{len(items)}] {line}", flush=True)
        except Exception as exc:  # noqa: BLE001
            failed.append(f"{object_id} {title} {exc}")
            print(f"[{index}/{len(items)}] FAIL {object_id} {exc}", flush=True)
    print(f"\n完成 {ok}，失败 {len(failed)}，目录 {ARTICLE_DIR}")
    if failed:
        (ARTICLE_DIR / "_failed.txt").write_text("\n".join(failed) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
