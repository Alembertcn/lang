#!/usr/bin/env python3
"""Build the read-only word book SQLite from the bundled JSON dictionaries.

The app queries one headword at a time. The three JSON files stay on disk for
the fetch scripts; they are not shipped in the app.
"""

from __future__ import annotations

import json
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "reference/moji-api/wordbook"
OUT = ROOT / "composeApp/src/commonMain/composeResources/files/wordbook.db"

SCHEMA = """
CREATE TABLE headword (
  id TEXT NOT NULL PRIMARY KEY,
  spell TEXT NOT NULL,
  pron TEXT NOT NULL,
  accent TEXT NOT NULL,
  romaji TEXT NOT NULL,
  excerpt TEXT NOT NULL,
  definition_ja TEXT NOT NULL
);
CREATE INDEX headword_spell ON headword(spell);
CREATE TABLE sense (
  word_id TEXT NOT NULL,
  sort_order INTEGER NOT NULL,
  zh TEXT NOT NULL,
  ja TEXT NOT NULL,
  examples_json TEXT NOT NULL,
  PRIMARY KEY (word_id, sort_order)
);
CREATE TABLE related_group (
  word_id TEXT NOT NULL,
  sort_order INTEGER NOT NULL,
  kind TEXT NOT NULL,
  words_json TEXT NOT NULL,
  PRIMARY KEY (word_id, sort_order)
);
"""


def rows(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    if OUT.exists():
        OUT.unlink()
    conn = sqlite3.connect(OUT)
    conn.executescript(SCHEMA)
    words = rows(SRC / "worddict.json")["words"]
    conn.executemany(
        "INSERT INTO headword VALUES (?,?,?,?,?,?,?)",
        (
            (
                str(item.get("i") or ""),
                str(item.get("s") or ""),
                str(item.get("p") or ""),
                str(item.get("a") or ""),
                str(item.get("r") or ""),
                str(item.get("x") or ""),
                str(item.get("j") or ""),
            )
            for item in words
            if item.get("i") and item.get("s")
        ),
    )
    senses = rows(SRC / "worddetail.json")
    sense_rows = []
    for word_id, groups in senses.items():
        if not isinstance(groups, list):
            continue
        for index, sense in enumerate(groups):
            if not isinstance(sense, list):
                continue
            examples = sense[2] if len(sense) > 2 else []
            sense_rows.append(
                (
                    word_id,
                    index,
                    str(sense[0] if len(sense) > 0 else ""),
                    str(sense[1] if len(sense) > 1 else ""),
                    json.dumps(examples, ensure_ascii=False, separators=(",", ":")),
                )
            )
    conn.executemany("INSERT INTO sense VALUES (?,?,?,?,?)", sense_rows)
    related = rows(SRC / "wordrelate.json")
    related_rows = []
    for word_id, groups in related.items():
        if not isinstance(groups, list):
            continue
        for index, group in enumerate(groups):
            if not isinstance(group, list) or len(group) < 2:
                continue
            related_rows.append(
                (
                    word_id,
                    index,
                    str(group[0]),
                    json.dumps(group[1], ensure_ascii=False, separators=(",", ":")),
                )
            )
    conn.executemany("INSERT INTO related_group VALUES (?,?,?,?)", related_rows)
    conn.execute("PRAGMA user_version = 1")
    conn.commit()
    sample = conn.execute(
        "SELECT kind FROM related_group WHERE word_id = ? ORDER BY sort_order",
        ("bVDvTDD9xZ",),
    ).fetchall()
    counts = {
        "headword": conn.execute("SELECT COUNT(*) FROM headword").fetchone()[0],
        "sense": conn.execute("SELECT COUNT(*) FROM sense").fetchone()[0],
        "related": conn.execute("SELECT COUNT(*) FROM related_group").fetchone()[0],
    }
    conn.close()
    print("wordbook", OUT, OUT.stat().st_size, counts, "母/いろ", [row[0] for row in sample])


if __name__ == "__main__":
    main()
