#!/usr/bin/env python3
"""Local media mock.

Serves reference/moji-api/oss like the original static host. A word mp3 that is
not on disk yet is synthesized with Qwen for that one request, then stored at
tts/{voiceId}/{wordId}.mp3 so the next play is the file.
"""

from __future__ import annotations

import re
import threading
import urllib.parse
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from pathlib import Path

import fetch_moji_word_audio_qwen as qwen

ROOT = Path(__file__).resolve().parents[1]
OSS = ROOT / "reference/moji-api/oss"
WORD_MP3 = re.compile(r"^/tts/(f002|m001|f004|m002)/([^/]+)\.mp3$")
_locks: dict[tuple[str, str], threading.Lock] = {}
_locks_guard = threading.Lock()


def file_lock(voice_id: str, word_id: str) -> threading.Lock:
    key = (voice_id, word_id)
    with _locks_guard:
        lock = _locks.get(key)
        if lock is None:
            lock = threading.Lock()
            _locks[key] = lock
        return lock


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(OSS), **kwargs)

    def send_head(self):
        path = urllib.parse.unquote(urllib.parse.urlsplit(self.path).path)
        match = WORD_MP3.match(path)
        if match:
            voice_id, word_id = match.group(1), match.group(2)
            dest = qwen.OUT / voice_id / f"{word_id}.mp3"
            if not (dest.exists() and dest.stat().st_size >= qwen.MIN_BYTES):
                with file_lock(voice_id, word_id):
                    if not (dest.exists() and dest.stat().st_size >= qwen.MIN_BYTES):
                        try:
                            ok = qwen.fill_one(voice_id, word_id)
                        except Exception as exc:
                            ok = False
                            self.log_message("qwen miss %s %s %s", voice_id, word_id, type(exc).__name__)
                        if ok:
                            self.log_message("qwen filled %s %s", voice_id, word_id)
        return super().send_head()


def main() -> None:
    server = ThreadingHTTPServer(("0.0.0.0", 8765), Handler)
    print("media mock on :8765", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
