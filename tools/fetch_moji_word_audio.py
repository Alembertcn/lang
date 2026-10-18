#!/usr/bin/env python3
"""Pull original word-card audio into the local media mock.

The app calls cloud function tts-fetch (tarId, tarType=102, voiceId) and plays
the mp3 it returns. That file is public at:

  https://oss.mojidict.com/tts_v2/102#{wordId}#{voiceId}.mp3

This script downloads that path for every id in worddict.json and the four
voice actors (f002 七海 / m001 恵太 / f004 葵 / m002 直樹). A 404 is retried
through tts-fetch, which is how the original app asks for a file that is not
already on the CDN. Existing non-empty files are skipped, so the run can resume.

Output (gitignored with the rest of the media mock):

  reference/moji-api/oss/tts/{voiceId}/{wordId}.mp3
  reference/moji-api/oss/tts/sample/{voiceId}.mp3
"""

from __future__ import annotations

import json
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

WORDDICT = ROOT / "reference/moji-api/wordbook/worddict.json"
OUT = ROOT / "reference/moji-api/oss/tts"
MISSING = OUT / "missing.txt"

VOICES = ("f002", "m001", "f004", "m002")
SAMPLE_ID = "9057e2e0a12e81e55f1fb9123e5ae006"
CDN = "https://oss.mojidict.com/tts_v2/"
WORKERS = 24
# tts-fetch 在 8 并发下会成片返回 HTTP 4xx/5xx，文件其实还在。补缺时放慢。
API_WORKERS = 2
API_TRIES = 4
MIN_BYTES = 200

_print_lock = threading.Lock()
_missing_lock = threading.Lock()


def log(message: str) -> None:
    with _print_lock:
        print(message, flush=True)


def cdn_url(target_id: str, voice_id: str) -> str:
    name = urllib.parse.quote(f"102#{target_id}#{voice_id}.mp3", safe="")
    return CDN + name


def sample_url(voice_id: str) -> str:
    name = urllib.parse.quote(f"{SAMPLE_ID}#{voice_id}.mp3", safe="")
    return CDN + name


def word_ids() -> list[str]:
    payload = json.loads(WORDDICT.read_text(encoding="utf-8"))
    seen: dict[str, None] = {}
    for item in payload["words"]:
        word_id = str(item.get("i") or "").strip()
        if word_id:
            seen.setdefault(word_id, None)
    return list(seen)


def fetch_bytes(url: str) -> bytes | None:
    req = urllib.request.Request(url, headers={"User-Agent": "moji-offline-fetch"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            blob = resp.read()
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            return None
        raise
    if len(blob) < MIN_BYTES or not (blob.startswith(b"ID3") or blob[:1] == b"\xff"):
        raise RuntimeError(f"not an mp3 ({len(blob)} bytes)")
    return blob


def save(url: str, dest: Path) -> str:
    if dest.exists() and dest.stat().st_size >= MIN_BYTES:
        return "skip"
    last_error = "err"
    for _ in range(3):
        try:
            blob = fetch_bytes(url)
        except Exception as exc:
            last_error = type(exc).__name__
            continue
        if blob is None:
            return "miss"
        dest.parent.mkdir(parents=True, exist_ok=True)
        tmp = dest.with_suffix(dest.suffix + ".part")
        tmp.write_bytes(blob)
        tmp.replace(dest)
        return "ok"
    return last_error


def note_missing(voice_id: str, word_id: str) -> None:
    with _missing_lock:
        MISSING.parent.mkdir(parents=True, exist_ok=True)
        with MISSING.open("a", encoding="utf-8") as handle:
            handle.write(f"{voice_id}\t{word_id}\n")


def api_fill(pairs: list[tuple[str, str]]) -> int:
    """Ask tts-fetch for files the public CDN does not have, then store the body."""
    if not pairs:
        return 0
    import fetch_moji_articles as articles
    import fetch_moji_plans as moji

    env = moji.load_env(moji.ENV_PATH)
    token_box = {"token": moji.login(env["MOJI_ACCOUNT"], env["MOJI_PASSWORD"])}
    token_lock = threading.Lock()
    filled = 0
    lock = threading.Lock()

    def refresh_token() -> str:
        with token_lock:
            token_box["token"] = moji.login(env["MOJI_ACCOUNT"], env["MOJI_PASSWORD"])
            return token_box["token"]

    def one(pair: tuple[str, str]) -> str:
        voice_id, word_id = pair
        dest = OUT / voice_id / f"{word_id}.mp3"
        if dest.exists() and dest.stat().st_size >= MIN_BYTES:
            return "skip"
        reason = "miss"
        for attempt in range(API_TRIES):
            with token_lock:
                token = token_box["token"]
            try:
                payload = articles.cloud(
                    "tts-fetch",
                    token,
                    {"tarId": word_id, "tarType": 102, "voiceId": voice_id},
                )
                url = payload["result"]["url"]
                blob = fetch_bytes(url)
            except urllib.error.HTTPError as exc:
                reason = f"HTTP {exc.code}"
                if exc.code == 401 and attempt + 1 < API_TRIES:
                    refresh_token()
                    time.sleep(1)
                    continue
                if exc.code in (429, 500, 502, 503, 504) and attempt + 1 < API_TRIES:
                    time.sleep(1.5 * (attempt + 1))
                    continue
                break
            except RuntimeError as exc:
                reason = str(exc)
                break
            except Exception as exc:
                reason = type(exc).__name__
                if attempt + 1 < API_TRIES:
                    time.sleep(1.5 * (attempt + 1))
                    continue
                break
            if not blob:
                reason = "no mp3"
                break
            dest.parent.mkdir(parents=True, exist_ok=True)
            tmp = dest.with_suffix(".part")
            tmp.write_bytes(blob)
            tmp.replace(dest)
            time.sleep(0.2)
            return "ok"
        log(f"api miss {voice_id} {word_id} {reason}")
        note_missing(voice_id, word_id)
        return "miss"

    done = 0
    with ThreadPoolExecutor(max_workers=API_WORKERS) as pool:
        futures = [pool.submit(one, pair) for pair in pairs]
        for future in as_completed(futures):
            if future.result() == "ok":
                with lock:
                    filled += 1
            done += 1
            if done % 200 == 0 or done == len(pairs):
                log(f"api {done}/{len(pairs)} filled {filled}")
    return filled


def main() -> None:
    ids = word_ids()
    log(f"words {len(ids)} voices {len(VOICES)} files {len(ids) * len(VOICES)}")
    for voice_id in VOICES:
        status = save(sample_url(voice_id), OUT / "sample" / f"{voice_id}.mp3")
        log(f"sample {voice_id} {status}")

    jobs = [(voice_id, word_id) for word_id in ids for voice_id in VOICES]
    counts = {"ok": 0, "skip": 0, "miss": 0, "err": 0}
    missed: list[tuple[str, str]] = []
    done = 0
    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        futures = {
            pool.submit(save, cdn_url(word_id, voice_id), OUT / voice_id / f"{word_id}.mp3"): (voice_id, word_id)
            for voice_id, word_id in jobs
        }
        for future in as_completed(futures):
            voice_id, word_id = futures[future]
            status = future.result()
            if status not in counts:
                counts["err"] += 1
            else:
                counts[status] += 1
            if status == "miss":
                missed.append((voice_id, word_id))
            elif status not in ("ok", "skip", "miss"):
                missed.append((voice_id, word_id))
            done += 1
            if done % 500 == 0 or done == len(jobs):
                log(f"{done}/{len(jobs)} {counts}")

    log(f"cdn done {counts} gaps {len(missed)}")
    filled = api_fill(missed)
    log(f"api filled {filled} still missing see {MISSING}")


if __name__ == "__main__":
    main()
