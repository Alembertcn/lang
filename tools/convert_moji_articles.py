#!/usr/bin/env python3
"""Convert fetched MOJi article bodies into app-consumable JSON.

Input : reference/moji-api/articles/{objectId}/body.html + detail.json
        reference/moji-api/audio-index.json
Output: composeApp/src/commonMain/composeResources/files/articles/{objectId}.json
        composeApp/src/commonMain/composeResources/files/articles/index.json

Each body.html carries the MOJi SRT model: <p srt-start=... srt-end=...> blocks
with ruby-annotated Japanese, each followed by a translation widget. We keep the
plain Japanese text (ruby base, rt/rp dropped), the Chinese translation and the
millisecond timeline so the app can play one continuous article audio with
sentence follow-highlight, exactly like the original player.
"""

from __future__ import annotations

import html
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
API_DIR = ROOT / "reference" / "moji-api"
ARTICLE_DIR = API_DIR / "articles"
OUT_DIR = ROOT / "composeApp/src/commonMain/composeResources/files/articles"

SRT_BLOCK = re.compile(
    r'<p[^>]*srt-start="([^"]+)"[^>]*srt-end="([^"]+)"[^>]*>(.*?)</p>',
    re.S,
)
TRANSLATION = re.compile(
    r'<div class="translation-main"[^>]*>(.*?)</div>',
    re.S,
)
RT = re.compile(r"<rt\b[^>]*>.*?</rt>", re.S)
RP = re.compile(r"<rp\b[^>]*>.*?</rp>", re.S)
TAG = re.compile(r"<[^>]+>")


def clean_text(fragment: str) -> str:
    text = RT.sub("", fragment)
    text = RP.sub("", text)
    text = TAG.sub("", text)
    text = html.unescape(text)
    text = text.replace("\u3000", " ")
    return re.sub(r"\s+", " ", text).strip()


def parse_timestamp(value: str) -> int:
    """00:01:06,037 -> milliseconds."""
    match = re.match(r"(\d+):(\d+):(\d+)[,.](\d+)", value.strip())
    if not match:
        return 0
    hours, minutes, seconds, millis = (int(part) for part in match.groups())
    return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis


def parse_article(body_html: str) -> list[dict]:
    """Walk the document in order and pair each SRT block with the next translation."""
    events: list[tuple[str, int, str, str]] = []
    for match in SRT_BLOCK.finditer(body_html):
        events.append(("srt", match.start(), match.group(1), match.group(3)))
        events.append(("srt-end", match.end(), match.group(2), ""))
    for match in TRANSLATION.finditer(body_html):
        events.append(("tr", match.start(), "", match.group(1)))
    events.sort(key=lambda item: item[1])

    sentences: list[dict] = []
    pending: dict | None = None
    for kind, _, first, second in events:
        if kind == "srt":
            if pending and pending["text"]:
                sentences.append(pending)
            pending = {
                "text": clean_text(second),
                "translation": "",
                "startMs": parse_timestamp(first),
                "endMs": 0,
            }
        elif kind == "srt-end" and pending is not None:
            pending["endMs"] = parse_timestamp(first)
        elif kind == "tr" and pending is not None and not pending["translation"]:
            pending["translation"] = clean_text(second)
    if pending and pending["text"]:
        sentences.append(pending)
    return sentences


def load_audio_index() -> dict[str, dict]:
    index_path = API_DIR / "audio-index.json"
    if not index_path.exists():
        return {}
    data = json.loads(index_path.read_text(encoding="utf-8"))
    return {item["objectId"]: item for item in data.get("items", [])}


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    audio_index = load_audio_index()

    index_entries = []
    converted = 0
    skipped = 0
    for article_dir in sorted(ARTICLE_DIR.iterdir()):
        if not article_dir.is_dir():
            continue
        object_id = article_dir.name
        body_path = article_dir / "body.html"
        detail_path = article_dir / "detail.json"
        if not body_path.exists():
            skipped += 1
            continue

        detail = {}
        if detail_path.exists():
            payload = json.loads(detail_path.read_text(encoding="utf-8"))
            detail = payload.get("result") or payload

        sentences = [s for s in parse_article(body_path.read_text(encoding="utf-8")) if s["text"]]
        if not sentences:
            skipped += 1
            continue

        audio = audio_index.get(object_id, {})
        article = {
            "objectId": object_id,
            "title": detail.get("title") or "",
            "excerpt": detail.get("excerpt") or "",
            "levelTags": detail.get("levelTags") or [],
            "charsNum": detail.get("charsNum") or 0,
            "audioId": audio.get("audioId") or detail.get("audioId") or "",
            "audioDurationMs": int((detail.get("audioDuration") or audio.get("audioDuration") or 0) * 1000),
            "sentences": [
                {
                    "order": i,
                    "text": s["text"],
                    "translation": s["translation"],
                    "startMs": s["startMs"],
                    "endMs": s["endMs"] or s["startMs"],
                }
                for i, s in enumerate(sentences)
            ],
        }
        (OUT_DIR / f"{object_id}.json").write_text(
            json.dumps(article, ensure_ascii=False, separators=(",", ":")),
            encoding="utf-8",
        )
        index_entries.append(
            {
                "objectId": object_id,
                "title": article["title"],
                "audioId": article["audioId"],
                "audioDurationMs": article["audioDurationMs"],
                "sentenceCount": len(sentences),
            }
        )
        converted += 1

    (OUT_DIR / "index.json").write_text(
        json.dumps({"articles": index_entries}, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    with_audio = sum(1 for e in index_entries if e["audioId"])
    print(f"converted: {converted}, skipped: {skipped}, with audio: {with_audio}")
    print(f"output: {OUT_DIR}")


if __name__ == "__main__":
    main()
