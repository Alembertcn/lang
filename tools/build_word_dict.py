#!/usr/bin/env python3
"""Bundle a word-card dictionary subset for the offline app.

The full MOJi dictionary (content/mojidict/dictionary.db, ~95k headwords) is too
big to ship, but the article corpus only ever taps a few thousand of them. This
script extracts exactly the headwords that appear in the bundled article bodies
(composeResources/files/article_detail/*.html) into a compact JSON asset that
WordDict.kt loads at runtime: headword id, spell, reading, accent, Chinese
excerpt and the Japanese definition used by the original word card.
"""
import json
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DB = ROOT / "content/mojidict/dictionary.db"
ARTICLES = ROOT / "composeApp/src/commonMain/composeResources/files/article_detail"
OUT = ROOT / "composeApp/src/commonMain/composeResources/files/worddict.json"

TAG = re.compile(r"<[^>]+>")
ENTITY = {"&amp;": "&", "&lt;": "<", "&gt;": ">", "&quot;": '"', "&#39;": "'",
          "&nbsp;": " ", "&#x27;": "'", "&#8217;": "'"}


def article_text() -> str:
    chunks = []
    for html in sorted(ARTICLES.glob("*.html")):
        raw = html.read_text(encoding="utf-8", errors="replace")
        raw = TAG.sub(" ", raw)
        for k, v in ENTITY.items():
            raw = raw.replace(k, v)
        chunks.append(raw)
    return "\n".join(chunks)


def main() -> int:
    text = article_text()
    con = sqlite3.connect(DB)
    ja = {}
    for word_id, title in con.execute(
            "SELECT word_id, title FROM subdetails WHERE lang='ja' AND title != ''"):
        ja.setdefault(word_id, title)
    words = []
    for oid, spell, pron, accent, excerpt in con.execute(
            "SELECT object_id, spell, pron, accent, excerpt FROM wort WHERE spell != ''"):
        if spell not in text:
            continue
        entry = {"i": oid, "s": spell}
        if pron:
            entry["p"] = pron
        if accent:
            entry["a"] = accent
        if excerpt:
            entry["x"] = excerpt
        definition = ja.get(oid)
        if definition:
            entry["j"] = definition
        words.append(entry)
    con.close()
    words.sort(key=lambda w: w["s"])
    OUT.write_text(json.dumps({"words": words}, ensure_ascii=False, separators=(",", ":")),
                   encoding="utf-8")
    print(f"words={len(words)} bytes={OUT.stat().st_size}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
