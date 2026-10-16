#!/usr/bin/env python3
"""Fetch the signed-in user's MOJi Read plan catalog.

Replays the 3.4.9 Android client:

  POST {API}/v1/account/unifiedLogin
       authName=PasswordAuth
       authPayload={email|mobile, countryCode?, code=<password>}
  GET  {API}/v1/readPlan/list?limit=&page=
  GET  {API}/v1/readPlan/mine
  GET  {API}/v1/readPlan/{planId}/detail

Credentials come from the repo-root .env (MOJI_ACCOUNT / MOJI_PASSWORD).
The session token is never printed. Raw JSON is written under output/, which is gitignored.
"""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENV_PATH = ROOT / ".env"
OUT_DIR = ROOT / "output"
API_DIR = ROOT / "reference" / "moji-api"
RESPONSE_DIR = API_DIR / "responses"

API = "https://api.mojidict.com/app/mojiread/api"
# Client id embedded in the APK (each character shifted +1). Not a user secret.
APP_ID = "".join(chr(ord(c) - 1) for c in "FXIlpf4qshpI8GQR{j{B")
CLIENT_KEY = "LT0BCBZjilbBFBz" + "cbk".upper() + "qFsex".replace("x", "Y") + "91cOhJr"


def load_env(path: Path) -> dict[str, str]:
    if not path.is_file():
        sys.exit(f"缺少 {path}。请写入 MOJI_ACCOUNT 和 MOJI_PASSWORD 后再运行。")
    env: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        env[key.strip()] = value.strip().strip('"').strip("'")
    return env


def headers(token: str | None) -> dict[str, str]:
    h = {
        "Content-Type": "application/json;charset=utf-8",
        "Accept": "application/json",
        "User-Agent": "Parse Android SDK API Level 36",
        "X-Parse-Application-Id": APP_ID,
        "X-Parse-Client-Key": CLIENT_KEY,
        "X-Parse-App-Build-Version": "385",
        "X-Parse-App-Display-Version": "3.4.9",
        "X-Parse-OS-Version": "12",
        "X-Parse-Installation-Id": os.environ.get("MOJI_INSTALLATION_ID", "moji-offline-fetch"),
        "X-MOJI-APP-ID": "com.mojidict.read",
        "X-MOJI-OS": "Android",
        "X-MOJI-APP-VERSION": "3.4.9",
        "X-MOJI-APP-BUILD": "385",
        "X-MOJI-APP-CHANNEL": "moji",
        "X-MOJI-APP-LANGUAGE": "zh-Hans",
        "X-MOJI-DEVICE-MODEL": "XQ-AT72",
        "X-MOJI-OS_VERSION": "12",
        "X-MOJI-DEVICE-ID": os.environ.get("MOJI_DEVICE_ID", str(uuid.uuid4())),
    }
    if token:
        h["X-MOJI-TOKEN"] = token
        h["X-Parse-Session-Token"] = token
    return h


def request(method: str, path: str, token: str | None, body: dict | None = None, query: dict | None = None):
    url = API + path
    if query:
        url += "?" + urllib.parse.urlencode(query)
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers(token), method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read().decode("utf-8")
            status = resp.status
    except urllib.error.HTTPError as err:
        raw = err.read().decode("utf-8", errors="replace")
        status = err.code
    try:
        parsed = json.loads(raw) if raw else None
    except json.JSONDecodeError:
        parsed = {"_raw": raw[:500]}
    return status, parsed


def unwrap(payload):
    """App deserializes the body directly; some gateways wrap {code, result}."""
    if isinstance(payload, dict) and "result" in payload and (
        "code" in payload or "sessionToken" not in payload
    ):
        return payload.get("result"), payload.get("code")
    return payload, None


def login(account: str, password: str) -> str:
    if "@" in account:
        payloads = [
            {"email": account, "code": password},
            {"email": account, "password": password},
        ]
    else:
        country = os.environ.get("MOJI_COUNTRY_CODE", "86")
        payloads = [
            {"mobile": account, "countryCode": country, "code": password},
            {"mobile": account, "countryCode": country, "password": password},
        ]
    last = None
    for auth_payload in payloads:
        status, body = request(
            "POST",
            "/v1/account/unifiedLogin",
            None,
            {"authName": "PasswordAuth", "authPayload": auth_payload},
        )
        last = (status, body)
        result, code = unwrap(body)
        token = None
        if isinstance(result, dict):
            token = result.get("sessionToken")
        if isinstance(body, dict) and not token:
            token = body.get("sessionToken")
        if token:
            return token
        # Business failure that is not "wrong field name" should stop.
        if isinstance(body, dict) and body.get("code") not in (None, 200, 0) and "password" not in auth_payload:
            break
    status, body = last
    hint = ""
    if isinstance(body, dict):
        hint = str(body.get("message") or body.get("code") or "")
    sys.exit(f"登录失败 HTTP {status} {hint}".strip())


def articles_by_id(articles) -> dict[str, dict]:
    out = {}
    if isinstance(articles, list):
        for item in articles:
            if isinstance(item, dict) and item.get("objectId"):
                out[item["objectId"]] = item
    return out


def chapter_lines(plan: dict, article_index: dict[str, dict]) -> list[str]:
    lines = []
    chapters = plan.get("chapters") or []
    if not isinstance(chapters, list):
        return lines
    for chapter in chapters:
        if not isinstance(chapter, dict):
            continue
        title = chapter.get("title") or "未命名章节"
        ids = chapter.get("artIds") or []
        lines.append(f"    {title}")
        if not isinstance(ids, list):
            continue
        for art_id in ids:
            article = article_index.get(art_id, {})
            name = article.get("title") or art_id
            lines.append(f"      - {name}")
    return lines


def summarize(plans: list[dict], article_index: dict[str, dict]) -> str:
    blocks = []
    for plan in plans:
        title = plan.get("title") or plan.get("objectId")
        art_num = plan.get("artNum")
        header = f"{title}" + (f"（{art_num} 篇）" if art_num else "")
        blocks.append(header)
        blocks.extend(chapter_lines(plan, article_index))
    return "\n".join(blocks)


def write_json(path: Path, payload) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    env = load_env(ENV_PATH)
    account = env.get("MOJI_ACCOUNT", "")
    password = env.get("MOJI_PASSWORD", "")
    if not account or not password:
        sys.exit("请在 .env 填写 MOJI_ACCOUNT 和 MOJI_PASSWORD。")
    os.environ["MOJI_COUNTRY_CODE"] = env.get("MOJI_COUNTRY_CODE", "86")

    token = login(account, password)

    plans: list[dict] = []
    article_index: dict[str, dict] = {}
    list_pages: list[dict] = []
    page = 1
    while page <= 20:
        status, body = request("GET", "/v1/readPlan/list", token, query={"limit": 20, "page": page})
        result, code = unwrap(body)
        if status != 200 or not isinstance(result, dict):
            sys.exit(f"计划列表失败 HTTP {status} code={code} body={json.dumps(body, ensure_ascii=False)[:300]}")
        list_pages.append(body if isinstance(body, dict) else {"result": result})
        article_index.update(articles_by_id(result.get("articles")))
        batch = result.get("readPlans") or []
        if isinstance(batch, list):
            plans.extend(item for item in batch if isinstance(item, dict))
        if not result.get("hasNext") or not batch:
            break
        page += 1

    status, mine_body = request("GET", "/v1/readPlan/mine", token)
    mine, _ = unwrap(mine_body)
    if status == 200 and isinstance(mine, dict):
        article_index.update(articles_by_id(mine.get("articles")))

    details: list[tuple[str, dict]] = []
    for plan in plans:
        plan_id = plan.get("objectId")
        if not plan_id:
            continue
        status, detail_body = request("GET", f"/v1/readPlan/{plan_id}/detail", token)
        detail, _ = unwrap(detail_body)
        if status == 200 and isinstance(detail, dict):
            article_index.update(articles_by_id(detail.get("articles")))
            inner = detail.get("readPlan")
            if isinstance(inner, dict):
                plan.update({k: v for k, v in inner.items() if v not in (None, [], "")})
            raw = detail_body if isinstance(detail_body, dict) else {"result": detail}
            details.append((plan_id, raw))

    OUT_DIR.mkdir(exist_ok=True)
    out_path = OUT_DIR / "moji-plans.json"
    out_path.write_text(
        json.dumps(
            {"plans": plans, "mine": mine if isinstance(mine, dict) else None, "details": [raw for _, raw in details]},
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    catalog = summarize(plans, article_index) or "计划列表为空"
    if len(list_pages) == 1:
        write_json(RESPONSE_DIR / "readPlan.list.json", list_pages[0])
    else:
        for index, page_body in enumerate(list_pages, start=1):
            write_json(RESPONSE_DIR / f"readPlan.list.page{index}.json", page_body)
    if isinstance(mine_body, dict):
        write_json(RESPONSE_DIR / "readPlan.mine.json", mine_body)
    detail_dir = RESPONSE_DIR / "readPlan.detail"
    for plan_id, raw in details:
        write_json(detail_dir / f"{plan_id}.json", raw)
    (API_DIR / "catalog.txt").write_text(catalog + "\n", encoding="utf-8")

    print(catalog)
    print(f"\n共 {len(plans)} 个计划，原文 JSON：{out_path}")
    print(f"接口响应：{RESPONSE_DIR}")


if __name__ == "__main__":
    main()
