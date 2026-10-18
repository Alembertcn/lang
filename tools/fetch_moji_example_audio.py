#!/usr/bin/env python3
"""Pull example pronunciation into the local media mock.

Word-detail playback uses tarType 103 and the Japanese example id:

  https://oss.mojidict.com/tts_v2/103#{exampleId}#{voiceId}.mp3

Files land at reference/moji-api/oss/tts/103/{voiceId}/{exampleId}.mp3,
which WordAudio.speakExample already requests. Existing files are skipped.
A 404 is recorded and left for a later tts-fetch pass; this script does not
call the account API.
"""

from __future__ import annotations

import json
import sys
import threading
import urllib.parse
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

import fetch_moji_word_audio as audio

WORDS = ROOT / "reference/moji-api/words"
OUT = audio.OUT / "103"
MISSING = OUT / "missing.txt"
WORKERS = 16

_print_lock = threading.Lock()
_missing_lock = threading.Lock()


def log(message: str) -> None:
    with _print_lock:
        print(message, flush=True)


def example_ids() -> list[str]:
    seen: dict[str, None] = {}
    for path in WORDS.glob("*.json"):
        try:
            body = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        for item in body.get("examples") or []:
            if not isinstance(item, dict) or item.get("lang") != "ja":
                continue
            example_id = str(item.get("objectId") or item.get("relaId") or "").strip()
            if example_id:
                seen.setdefault(example_id, None)
    return list(seen)


def note_missing(voice_id: str, example_id: str) -> None:
    with _missing_lock:
        MISSING.parent.mkdir(parents=True, exist_ok=True)
        with MISSING.open("a", encoding="utf-8") as handle:
            handle.write(f"{voice_id}\t{example_id}\n")


def fetch_one(voice_id: str, example_id: str) -> str:
    dest = OUT / voice_id / f"{example_id}.mp3"
    url = audio.CDN + urllib.parse.quote(f"103#{example_id}#{voice_id}.mp3", safe="")
    status = audio.save(url, dest)
    if status == "miss":
        note_missing(voice_id, example_id)
    return status


def main() -> None:
    ids = example_ids()
    jobs = [
        (voice_id, example_id)
        for example_id in ids
        for voice_id in audio.VOICES
        if not (OUT / voice_id / f"{example_id}.mp3").exists()
    ]
    log(f"examples {len(ids)} voices {len(audio.VOICES)} pending {len(jobs)}")
    if not jobs:
        return
    ok = skip = miss = fail = 0
    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        futures = [pool.submit(fetch_one, voice_id, example_id) for voice_id, example_id in jobs]
        done = 0
        for future in as_completed(futures):
            done += 1
            status = future.result()
            if status == "ok":
                ok += 1
            elif status == "skip":
                skip += 1
            elif status == "miss":
                miss += 1
            else:
                fail += 1
            if done % 500 == 0 or done == len(jobs):
                log(f"{done}/{len(jobs)} ok={ok} skip={skip} miss={miss} fail={fail}")
    log(f"done ok={ok} miss={miss} fail={fail}")


if __name__ == "__main__":
    main()
