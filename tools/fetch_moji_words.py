#!/usr/bin/env python3
"""Pull GET /v1/word/{id} into the local mock.

Article headwords are fetched first. Then every id named by 关联词
(synonyms, paronyms, polyphonics, antonyms, intransitive, transitive,
subject) is fetched too, and that repeats until those new records name
no further missing ids. Raw bodies land in

  reference/moji-api/words/{id}.json

Existing files are skipped, so the run can resume. The session token is never
printed. After the download, worddict / worddetail / wordrelate are rebuilt
from the cache so the offline detail page can open the linked words.
"""

from __future__ import annotations

import json
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

import build_word_dict as builder
import fetch_moji_plans as moji

WORDDICT = ROOT / "reference/moji-api/wordbook/worddict.json"
DETAIL = ROOT / "reference/moji-api/wordbook/worddetail.json"
OUT = ROOT / "reference/moji-api/words"
WORKERS = 16

_print_lock = threading.Lock()


def log(message: str) -> None:
    with _print_lock:
        print(message, flush=True)


def word_ids() -> list[str]:
    payload = json.loads(WORDDICT.read_text(encoding="utf-8"))
    return [str(item["i"]) for item in payload["words"] if item.get("i")]


def referenced_ids() -> set[str]:
    """Word ids named by already-cached 关联词. These are what the detail page opens."""
    found: set[str] = set()

    def add(value) -> None:
        if isinstance(value, str) and value.strip():
            found.add(value.strip())

    for path in OUT.glob("*.json"):
        try:
            body = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        related = body.get("relatedWord") if isinstance(body, dict) else None
        if not isinstance(related, dict):
            continue
        for key in ("synonyms", "paronyms", "polyphonics", "antonyms", "intransitive", "transitive"):
            value = related.get(key)
            if isinstance(value, dict):
                value = [value]
            if not isinstance(value, list):
                continue
            for item in value:
                if isinstance(item, dict):
                    add(item.get("objectId"))
        for item in related.get("subject") or []:
            if isinstance(item, dict):
                add(item.get("relatedId") or item.get("relateId"))
    return found


def save_word(token: str, word_id: str) -> str:
    dest = OUT / f"{word_id}.json"
    if dest.exists() and dest.stat().st_size > 20:
        return "skip"
    last = "err"
    for attempt in range(3):
        try:
            status, body = moji.request("GET", f"/v1/word/{word_id}", token)
        except Exception as exc:
            last = type(exc).__name__
            time.sleep(0.4 * (attempt + 1))
            continue
        if status in (401, 403):
            return f"auth {status}"
        if status != 200 or not isinstance(body, dict):
            last = f"http {status}"
            time.sleep(0.4 * (attempt + 1))
            continue
        payload, _code = moji.unwrap(body)
        record = payload if isinstance(payload, dict) else body
        if not isinstance(record, dict):
            last = "bad body"
            continue
        dest.parent.mkdir(parents=True, exist_ok=True)
        tmp = dest.with_suffix(".json.part")
        tmp.write_text(json.dumps(record, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        tmp.replace(dest)
        return "ok"
    return last


def merge_bundle() -> None:
    details = json.loads(DETAIL.read_text(encoding="utf-8")) if DETAIL.exists() else {}
    merged = builder.merge_word_cache(details)
    DETAIL.write_text(json.dumps(details, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    added = builder.upsert_worddict_from_cache()
    related = builder.write_related()
    log(f"bundle senses={merged} headwords+={added} related={related}")


def fetch_ids(token: str, pending: list[str]) -> tuple[str, int]:
    """Returns (token, fail count). Refreshes the token once if it is rejected."""
    ok = skip = fail = 0
    auth_stop = False
    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        futures = {pool.submit(save_word, token, word_id): word_id for word_id in pending}
        done = 0
        for future in as_completed(futures):
            done += 1
            status = future.result()
            if status == "ok":
                ok += 1
            elif status == "skip":
                skip += 1
            else:
                fail += 1
                if status.startswith("auth"):
                    auth_stop = True
                    log(f"stop {futures[future]} {status}")
                    break
            if done % 200 == 0 or done == len(pending):
                log(f"{done}/{len(pending)} ok={ok} skip={skip} fail={fail}")
        if auth_stop:
            pool.shutdown(wait=False, cancel_futures=True)
            env = moji.load_env(moji.ENV_PATH)
            token = moji.login(env["MOJI_ACCOUNT"], env["MOJI_PASSWORD"])
            log("token refreshed")
    return token, fail


def main() -> None:
    cached = {path.stem for path in OUT.glob("*.json")}
    seeds = [word_id for word_id in word_ids() if word_id not in cached]
    log(f"cached {len(cached)} article-headwords pending {len(seeds)}")
    env = moji.load_env(moji.ENV_PATH)
    token = moji.login(env["MOJI_ACCOUNT"], env["MOJI_PASSWORD"])
    if seeds:
        token, fail = fetch_ids(token, seeds)
        if fail:
            log(f"article headword failures {fail}")
    wave = 0
    while True:
        cached = {path.stem for path in OUT.glob("*.json")}
        pending = sorted(referenced_ids() - cached)
        log(f"wave {wave} related pending {len(pending)} cached {len(cached)}")
        if not pending:
            break
        token, fail = fetch_ids(token, pending)
        wave += 1
        if fail and wave > 8:
            log(f"stop after repeated failures {fail}")
            break
        if wave > 12:
            log("stop after 12 waves")
            break
    merge_bundle()


if __name__ == "__main__":
    main()
