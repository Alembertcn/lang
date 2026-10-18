#!/usr/bin/env python3
"""Fill word-audio gaps with Qwen TTS, into the same mock paths.

Playback stays `tts/{voiceId}/{wordId}.mp3`. This script only writes files that
tts-fetch still does not have. The spoken string is the dictionary kana
(`pron`), then the headword when that has no kana. Voices are Qwen stand-ins
for the four original actors, not the original timbre.

Uses `qwen-audio-3.1-tts-flash` via SpeechSynthesizer + language_hints=ja
(more stable on short Japanese than qwen3-tts-flash). Model/base from .env.

  f002 七海 → longanfengyue_v3.1 龙安风悦
  f004 葵   → longanhuan_v3.1    龙安欢
  m001 恵太 → xunanchuan_v3.1    许南川
  m002 直樹 → xunanchuan_v3.1    许南川
  （多语种音色里只有一个正式支持日语的男声，两档男声共用。）
"""

from __future__ import annotations

import base64
import json
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

import fetch_moji_plans as moji  # noqa: E402

WORDDICT = ROOT / "reference/moji-api/wordbook/worddict.json"
OUT = ROOT / "reference/moji-api/oss/tts"
DEFAULT_MODEL = "qwen-audio-3.1-tts-flash"
DEFAULT_BASE = "https://dashscope.aliyuncs.com/api/v1"
WORKERS = 2
MIN_BYTES = 200
TRIES = 4

# Original voice id → Qwen-Audio-TTS multilingual voice (Japanese via language_hints).
QWEN_VOICE = {
    "f002": "longanfengyue_v3.1",
    "f004": "longanhuan_v3.1",
    "m001": "xunanchuan_v3.1",
    "m002": "xunanchuan_v3.1",
}

_print_lock = threading.Lock()


def log(message: str) -> None:
    with _print_lock:
        print(message, flush=True)


def spoken(row: dict) -> str:
    pron = str(row.get("p") or "").strip()
    spell = str(row.get("s") or "").strip()
    if any("\u3040" <= ch <= "\u30ff" for ch in pron):
        return pron
    if any("\u3040" <= ch <= "\u30ff" for ch in spell):
        return spell
    return pron or spell


_rows: dict[str, dict] | None = None
_rows_lock = threading.Lock()


def word_rows() -> dict[str, dict]:
    global _rows
    with _rows_lock:
        if _rows is None:
            payload = json.loads(WORDDICT.read_text(encoding="utf-8"))
            _rows = {str(item["i"]): item for item in payload["words"] if item.get("i")}
        return _rows


def tts_config() -> tuple[str, str, str]:
    env = moji.load_env(moji.ENV_PATH)
    key = env.get("QWEN_API_KEY", "").strip()
    base = env.get("QWEN_TTS_BASE_URL", "").strip() or DEFAULT_BASE
    model = env.get("QWEN_TTS_MODEL", "").strip() or DEFAULT_MODEL
    return key, base, model


def fill_one(voice_id: str, word_id: str) -> bool:
    """Synthesize one missing word mp3. Existing files are left as they are."""
    if voice_id not in QWEN_VOICE:
        return False
    dest = OUT / voice_id / f"{word_id}.mp3"
    if dest.exists() and dest.stat().st_size >= MIN_BYTES:
        return True
    text = spoken(word_rows().get(word_id) or {})
    if not text:
        return False
    key, base, model = tts_config()
    if not key:
        return False
    wav = qwen_wav(text, QWEN_VOICE[voice_id], key, base, model)
    to_mp3(wav, dest)
    return dest.exists() and dest.stat().st_size >= MIN_BYTES


def gaps() -> list[tuple[str, str, str]]:
    rows = word_rows()
    pending: list[tuple[str, str, str]] = []
    for voice_id in QWEN_VOICE:
        folder = OUT / voice_id
        for word_id, row in rows.items():
            dest = folder / f"{word_id}.mp3"
            if dest.exists() and dest.stat().st_size >= MIN_BYTES:
                continue
            text = spoken(row)
            if text:
                pending.append((voice_id, word_id, text))
    return pending


def qwen_wav(text: str, voice: str, key: str, base: str, model: str) -> bytes:
    body = {
        "model": model,
        "input": {
            "text": text,
            "voice": voice,
            "language_hints": ["ja"],
            "format": "wav",
            "sample_rate": 24000,
        },
    }
    req = urllib.request.Request(
        base.rstrip("/") + "/services/audio/tts/SpeechSynthesizer",
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    audio = (payload.get("output") or {}).get("audio") or {}
    encoded = audio.get("data")
    if encoded:
        return base64.b64decode(encoded)
    url = audio.get("url")
    if not url:
        code = payload.get("code") or "no audio"
        raise RuntimeError(str(code))
    url = url.replace("http://", "https://", 1)
    with urllib.request.urlopen(url, timeout=60) as resp:
        return resp.read()


def to_mp3(wav: bytes, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(".part.mp3")
    proc = subprocess.run(
        [
            "ffmpeg",
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-i",
            "pipe:0",
            "-codec:a",
            "libmp3lame",
            "-ar",
            "24000",
            "-ac",
            "1",
            "-b:a",
            "96k",
            str(tmp),
        ],
        input=wav,
        capture_output=True,
    )
    if proc.returncode != 0 or not tmp.exists() or tmp.stat().st_size < MIN_BYTES:
        tmp.unlink(missing_ok=True)
        err = (proc.stderr or b"").decode("utf-8", "replace")[:200]
        raise RuntimeError(f"ffmpeg failed {err}")
    tmp.replace(dest)


def main() -> None:
    if "--all" not in sys.argv:
        log("qwen batch skipped; a missing word is synthesized when it is played")
        return
    key, base, model = tts_config()
    if not key:
        raise SystemExit("QWEN_API_KEY is empty")
    pending = gaps()
    log(f"qwen gaps {len(pending)} model {model}")
    if not pending:
        return
    filled = 0
    lock = threading.Lock()

    def one(item: tuple[str, str, str]) -> str:
        voice_id, word_id, text = item
        dest = OUT / voice_id / f"{word_id}.mp3"
        if dest.exists() and dest.stat().st_size >= MIN_BYTES:
            return "skip"
        reason = "miss"
        for attempt in range(TRIES):
            try:
                wav = qwen_wav(text, QWEN_VOICE[voice_id], key, base, model)
                to_mp3(wav, dest)
                return "ok"
            except urllib.error.HTTPError as exc:
                reason = f"HTTP {exc.code}"
                if exc.code in (429, 500, 502, 503, 504) and attempt + 1 < TRIES:
                    time.sleep(1.5 * (attempt + 1))
                    continue
                break
            except Exception as exc:
                reason = type(exc).__name__
                if attempt + 1 < TRIES:
                    time.sleep(1.5 * (attempt + 1))
                    continue
                break
        log(f"qwen miss {voice_id} {word_id} {reason}")
        return "miss"

    done = 0
    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        futures = [pool.submit(one, item) for item in pending]
        for future in as_completed(futures):
            if future.result() == "ok":
                with lock:
                    filled += 1
            done += 1
            if done % 50 == 0 or done == len(pending):
                log(f"qwen {done}/{len(pending)} filled {filled}")
    log(f"qwen filled {filled} of {len(pending)}")


if __name__ == "__main__":
    main()
