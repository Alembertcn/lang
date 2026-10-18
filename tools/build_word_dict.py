#!/usr/bin/env python3
"""Bundle a word-card dictionary subset for the offline app.

Build wordbook JSON under reference/moji-api/wordbook/.

Preferred path: rebuild from the captured word API cache
(reference/moji-api/words/*.json) via upsert/merge helpers — no dictionary.db.

Legacy path (if content/mojidict/dictionary.db still exists): filter headwords
against bundled article bodies / lemmas, then merge the word cache on top.
Runtime app uses wordbook.db (tools/build_word_book.py), not these JSON files.
"""
import json
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DB = ROOT / "content/mojidict/dictionary.db"
ARTICLES = ROOT / "composeApp/src/commonMain/composeResources/files/article_detail"
OUT = ROOT / "reference/moji-api/wordbook/worddict.json"
DETAIL_OUT = ROOT / "reference/moji-api/wordbook/worddetail.json"
RELATE_OUT = ROOT / "reference/moji-api/wordbook/wordrelate.json"

TAG = re.compile(r"<[^>]+>")
LEMMA = re.compile(r'\blemma="([^"]*)"')
# 原版例句 `notationTitle`：ruby / moji-toolkit-org 带 lemma，点词走 callWordAlert。
NOTATION_PART = re.compile(
    r"<ruby[^>]*>\s*<rb>(.*?)</rb>.*?<rt([^>]*)>.*?</rt>.*?</ruby>"
    r'|<span[^>]*class="[^"]*moji-toolkit-org[^"]*"[^>]*>(.*?)</span>'
    r"|([^<]+)",
    re.I | re.S,
)
ENTITY = {"&amp;": "&", "&lt;": "<", "&gt;": ">", "&quot;": '"', "&#39;": "'",
          "&nbsp;": " ", "&#x27;": "'", "&#8217;": "'"}


def unescape(text: str) -> str:
    for k, v in ENTITY.items():
        text = text.replace(k, v)
    return text


def notation_tokens(html: str) -> list:
    """Compact clickable tokens from notationTitle.

    Each item is either a bare surface string (lemma == surface) or
    [surface, lemma]. Word-detail Compose reconstructs the sentence and
    opens the word card with lemma-first lookup, matching callWordAlert.
    """
    if not html:
        return []
    out: list = []
    for match in NOTATION_PART.finditer(html):
        if match.group(1) is not None:
            surface = unescape(TAG.sub("", match.group(1)))
            lemma_m = LEMMA.search(match.group(2) or "")
            lemma = (lemma_m.group(1) if lemma_m else surface).split("-", 1)[0].strip() or surface
        elif match.group(3) is not None:
            surface = unescape(TAG.sub("", match.group(3)))
            lemma_m = LEMMA.search(match.group(0))
            lemma = (lemma_m.group(1) if lemma_m else surface).split("-", 1)[0].strip() or surface
        else:
            surface = unescape(match.group(4) or "")
            lemma = surface
        if surface == "":
            continue
        out.append(surface if lemma == surface else [surface, lemma])
    return out


def article_text() -> str:
    chunks = []
    for html in sorted(ARTICLES.glob("*.html")):
        raw = html.read_text(encoding="utf-8", errors="replace")
        raw = TAG.sub(" ", raw)
        for k, v in ENTITY.items():
            raw = raw.replace(k, v)
        chunks.append(raw)
    return "\n".join(chunks)


def lemma_keys() -> set[str]:
    """Dictionary forms marked on tokens, e.g. 盗ま -> lemma=\"盗む\"."""
    keys: set[str] = set()
    for html in ARTICLES.glob("*.html"):
        raw = html.read_text(encoding="utf-8", errors="replace")
        for match in LEMMA.finditer(raw):
            key = match.group(1).split("-", 1)[0].strip()
            if key:
                keys.add(key)
    return keys


def chunks(items: list[str], size: int):
    for start in range(0, len(items), size):
        yield items[start:start + size]


def build_details(con: sqlite3.Connection, ids: list[str]) -> dict:
    """Senses and examples the original word-detail page loads for these ids.

    Subdetails group by rela_id (Chinese gloss + Japanese gloss). Examples
    under that sense pair the same way: Japanese sentence + Chinese translation.
    """
    subs_by_detail: dict[tuple[str, str], list[tuple]] = {}
    details_by_word: dict[str, list[str]] = {}
    examples_by_sub: dict[str, dict[str, dict[str, str]]] = {}
    for chunk in chunks(ids, 400):
        marks = ",".join("?" * len(chunk))
        for oid, wid in con.execute(
                f"SELECT object_id, word_id FROM details WHERE word_id IN ({marks}) ORDER BY rowid",
                chunk):
            details_by_word.setdefault(wid, []).append(oid)
        for oid, rela, did, wid, lang, title in con.execute(
                f"""SELECT object_id, rela_id, details_id, word_id, lang, title
                    FROM subdetails WHERE word_id IN ({marks}) ORDER BY rowid""",
                chunk):
            subs_by_detail.setdefault((wid, did or ""), []).append((oid, rela or oid, lang or "", title or ""))
        for rela, sid, lang, title in con.execute(
                f"""SELECT rela_id, subdetails_id, lang, title
                    FROM example WHERE word_id IN ({marks}) AND title != '' ORDER BY rowid""",
                chunk):
            if not sid:
                continue
            slot = examples_by_sub.setdefault(sid, {}).setdefault(rela or sid, {"ja": "", "zh": ""})
            if lang == "ja" and not slot["ja"]:
                slot["ja"] = title
            elif (lang or "").startswith("zh") and not slot["zh"]:
                slot["zh"] = title

    out: dict[str, list] = {}
    for wid in ids:
        senses = []
        detail_ids = details_by_word.get(wid) or [""]
        seen = set()
        for did in detail_ids:
            items = subs_by_detail.get((wid, did), [])
            zh_rows = [row for row in items if row[2].startswith("zh") and row[3].strip()]
            ja_by_rela = {}
            ja_oid = {}
            for oid, rela, lang, title in items:
                if lang == "ja" and title.strip():
                    ja_by_rela.setdefault(rela, title.strip())
                    ja_oid.setdefault(rela, oid)
            rows = zh_rows or [
                (oid, rela, lang, title)
                for oid, rela, lang, title in items
                if lang == "ja" and title.strip()
            ]
            for oid, rela, lang, title in rows:
                if oid in seen:
                    continue
                seen.add(oid)
                if lang.startswith("zh"):
                    zh = title.strip()
                    ja = ja_by_rela.get(oid) or ja_by_rela.get(rela) or ""
                    example_ids = {oid}
                    twin = ja_oid.get(oid) or ja_oid.get(rela)
                    if twin:
                        example_ids.add(twin)
                else:
                    zh = ""
                    ja = title.strip()
                    example_ids = {oid}
                pairs = []
                used = set()
                for sid in example_ids:
                    for key, pair in examples_by_sub.get(sid, {}).items():
                        if key in used or not (pair["ja"] or pair["zh"]):
                            continue
                        used.add(key)
                        pairs.append([pair["ja"], pair["zh"]])
                if zh or ja:
                    senses.append([zh, ja, pairs])
        if senses:
            out[wid] = senses
    return out


WORD_CACHE = ROOT / "reference" / "moji-api" / "words"


def senses_from_payload(body: dict) -> list:
    """Senses from a captured GET /v1/word/{id} body.

    The bundled dictionary drops subdetail rows the original fills from this
    response (た has only 《助動》 locally, sixteen glosses online).

    Display order is `word.exampleIds` / `word.subdetailsIds`, the same lists
    the word-detail H5 sorts with before painting. The examples array itself
    is not display order (午前 is fourth in the array and first on screen).
    The third example field is the Japanese example id, which native playback
    sends as tarType 103.
    """
    word = body.get("word") if isinstance(body.get("word"), dict) else {}
    sub_rank = {str(item): index for index, item in enumerate(word.get("subdetailsIds") or [])}
    example_rank = {str(item): index for index, item in enumerate(word.get("exampleIds") or [])}
    order: list[str] = []
    zh: dict[str, str] = {}
    ja: dict[str, str] = {}
    ids_for_rela: dict[str, list[str]] = {}
    for sub in body.get("subdetails") or []:
        if not isinstance(sub, dict):
            continue
        rela = str(sub.get("relaId") or sub.get("objectId") or "")
        oid = str(sub.get("objectId") or "")
        lang = str(sub.get("lang") or "")
        title = str(sub.get("title") or "").strip()
        if not rela:
            continue
        if rela not in zh and rela not in ja:
            order.append(rela)
        if oid and oid not in ids_for_rela.setdefault(rela, []):
            ids_for_rela[rela].append(oid)
        if lang.startswith("zh") and title and rela not in zh:
            zh[rela] = title
        elif lang == "ja" and title and rela not in ja:
            ja[rela] = title
    examples: dict[str, dict[str, dict[str, str]]] = {}
    for item in body.get("examples") or []:
        if not isinstance(item, dict):
            continue
        sid = str(item.get("subdetailsId") or "")
        key = str(item.get("relaId") or sid)
        title = str(item.get("title") or "").strip()
        lang = str(item.get("lang") or "")
        if not sid or not title or not key:
            continue
        slot = examples.setdefault(sid, {}).setdefault(
            key, {"ja": "", "zh": "", "id": key, "tok": []},
        )
        if lang == "ja" and not slot["ja"]:
            slot["ja"] = title
            ja_id = str(item.get("objectId") or key)
            if ja_id:
                slot["id"] = ja_id
            nota = str(item.get("notationTitle") or "")
            if nota:
                slot["tok"] = notation_tokens(nota)
        elif lang.startswith("zh") and not slot["zh"]:
            slot["zh"] = title
    if sub_rank:
        position = {rela: index for index, rela in enumerate(order)}
        order.sort(key=lambda rela: (sub_rank.get(rela, len(sub_rank)), position[rela]))
    senses = []
    for rela in order:
        ranked: list[tuple[int, list]] = []
        used: set[str] = set()
        # examples[].subdetailsId is often the sense relaId (cFOP12kuI7), and
        # sometimes a subdetail objectId / "0". Try every known id for this sense.
        candidate_sids = list(dict.fromkeys([*ids_for_rela.get(rela, []), rela]))
        for sid in candidate_sids:
            for key, pair in examples.get(sid, {}).items():
                if key in used or not (pair["ja"] or pair["zh"]):
                    continue
                used.add(key)
                # exampleIds lists relaId. Playback id is the Japanese objectId
                # (H5 `titleId`, native `Example.getPk()`).
                # 第四段是 notation 分词，详情页例句点词用。
                row: list = [pair["ja"], pair["zh"], pair["id"]]
                if pair.get("tok"):
                    row.append(pair["tok"])
                ranked.append((example_rank.get(key, len(example_rank)), row))
        ranked.sort(key=lambda item: item[0])
        pairs = [item for _, item in ranked]
        gloss_zh = zh.get(rela, "")
        gloss_ja = ja.get(rela, "")
        if gloss_zh or gloss_ja:
            senses.append([gloss_zh, gloss_ja, pairs])
    return senses


def related_from_payload(body: dict) -> list:
    """关联词 groups in the order the original H5 assigns them.

    Codes: i 自动词, t 他动词, a 同词位, p 多音词, y 近义词, n 反义词, s 话题词.
    Each item is [spell, pron-or-translation, wordId].
    """
    related = body.get("relatedWord")
    if not isinstance(related, dict):
        return []

    def words(value, subject: bool = False) -> list:
        if isinstance(value, dict):
            value = [value]
        if not isinstance(value, list):
            return []
        items = []
        for item in value:
            if not isinstance(item, dict):
                continue
            spell = str((item.get("title") if subject else item.get("spell")) or "").strip()
            extra = str((item.get("trans") if subject else item.get("pron")) or "").strip()
            word_id = str((item.get("relatedId") if subject else item.get("objectId")) or "").strip()
            if subject and not word_id:
                word_id = str(item.get("relateId") or "").strip()
            if spell:
                items.append([spell, extra, word_id])
        return items

    groups = []
    for key, code, subject in (
        ("intransitive", "i", False),
        ("transitive", "t", False),
        ("paronyms", "a", False),
        ("polyphonics", "p", False),
        ("synonyms", "y", False),
        ("antonyms", "n", False),
        ("subject", "s", True),
    ):
        items = words(related.get(key), subject)
        if items:
            groups.append([code, items])
    return groups


def headword_from_record(word_id: str, body: dict) -> dict | None:
    """Compact worddict row from a captured GET /v1/word body."""
    word = body.get("word") if isinstance(body, dict) else None
    if not isinstance(word, dict):
        return None
    spell = str(word.get("spell") or "").strip()
    if not spell:
        return None
    entry = {"i": word_id, "s": spell}
    pron = str(word.get("pron") or "").strip()
    accent = str(word.get("accent") or "").strip()
    romaji = str(word.get("romaji_hepburn_CN") or word.get("romaji_hepburn") or "").strip()
    excerpt = str(word.get("excerpt") or "").strip()
    if pron:
        entry["p"] = pron
    if accent:
        entry["a"] = accent
    if romaji:
        entry["r"] = romaji
    if excerpt:
        entry["x"] = excerpt
    for sub in body.get("subdetails") or []:
        if isinstance(sub, dict) and sub.get("lang") == "ja" and str(sub.get("title") or "").strip():
            entry["j"] = str(sub["title"]).strip()
            break
    return entry


def upsert_worddict_from_cache() -> int:
    """Add captured words that are not article headwords, so 关联词 can open them."""
    payload = json.loads(OUT.read_text(encoding="utf-8")) if OUT.exists() else {"words": []}
    rows = payload.get("words") or []
    by_id = {str(item["i"]): item for item in rows if item.get("i")}
    added = 0
    if WORD_CACHE.is_dir():
        for path in WORD_CACHE.glob("*.json"):
            try:
                body = json.loads(path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                continue
            entry = headword_from_record(path.stem, body)
            if entry is None:
                continue
            old = by_id.get(path.stem)
            if old is None:
                by_id[path.stem] = entry
                added += 1
                continue
            for key, value in entry.items():
                if key != "i" and value and not old.get(key):
                    old[key] = value
    payload["words"] = sorted(by_id.values(), key=lambda item: item["s"])
    OUT.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    return added


def write_related() -> int:
    """Bundle relatedWord from the captured GET /v1/word cache."""
    payload: dict = {}
    if WORD_CACHE.is_dir():
        for path in sorted(WORD_CACHE.glob("*.json")):
            try:
                body = json.loads(path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                continue
            if not isinstance(body, dict):
                continue
            groups = related_from_payload(body)
            if groups:
                payload[path.stem] = groups
    RELATE_OUT.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
                          encoding="utf-8")
    return len(payload)


def merge_word_cache(details: dict) -> int:
    """Prefer a captured full word when it has more senses than the local DB."""
    if not WORD_CACHE.is_dir():
        return 0
    merged = 0
    for path in sorted(WORD_CACHE.glob("*.json")):
        try:
            body = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        if not isinstance(body, dict):
            continue
        senses = senses_from_payload(body)
        # Equal counts still replace: the cache carries exampleIds order and
        # the Japanese example id used for tarType 103 playback.
        if senses and len(senses) >= len(details.get(path.stem) or []):
            details[path.stem] = senses
            merged += 1
    return merged


def rebuild_from_word_cache() -> int:
    """Rebuild wordbook JSON from reference/moji-api/words (no dictionary.db)."""
    details: dict = {}
    if DETAIL_OUT.exists():
        try:
            details = json.loads(DETAIL_OUT.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            details = {}
    merged = merge_word_cache(details)
    DETAIL_OUT.write_text(json.dumps(details, ensure_ascii=False, separators=(",", ":")),
                          encoding="utf-8")
    added = upsert_worddict_from_cache()
    related = write_related()
    words = 0
    if OUT.exists():
        try:
            words = len(json.loads(OUT.read_text(encoding="utf-8")).get("words") or [])
        except json.JSONDecodeError:
            words = 0
    print(f"cache-only words={words} headwords+={added} details={len(details)} "
          f"cached={merged} related={related}")
    return 0


def main() -> int:
    # Preferred path: mock word cache only (dictionary.db is optional / retired).
    if not DB.exists():
        if not WORD_CACHE.is_dir() or not any(WORD_CACHE.glob("*.json")):
            print(f"missing {DB} and {WORD_CACHE}/*.json; fetch words first "
                  f"(tools/fetch_moji_words.py)", file=sys.stderr)
            return 1
        return rebuild_from_word_cache()

    text = article_text()
    keys = lemma_keys()
    con = sqlite3.connect(DB)
    ja = {}
    for word_id, title in con.execute(
            "SELECT word_id, title FROM subdetails WHERE lang='ja' AND title != ''"):
        ja.setdefault(word_id, title)
    words = []
    for oid, spell, pron, accent, romaji, excerpt in con.execute(
            "SELECT object_id, spell, pron, accent, romaji, excerpt FROM wort WHERE spell != ''"):
        if spell not in text and spell not in keys:
            continue
        entry = {"i": oid, "s": spell}
        if pron:
            entry["p"] = pron
        if accent:
            entry["a"] = accent
        if romaji:
            entry["r"] = romaji
        if excerpt:
            entry["x"] = excerpt
        definition = ja.get(oid)
        if definition:
            entry["j"] = definition
        words.append(entry)
    ids = list(dict.fromkeys(entry["i"] for entry in words))
    details = build_details(con, ids)
    con.close()
    merged = merge_word_cache(details)
    words.sort(key=lambda w: w["s"])
    OUT.write_text(json.dumps({"words": words}, ensure_ascii=False, separators=(",", ":")),
                   encoding="utf-8")
    DETAIL_OUT.write_text(json.dumps(details, ensure_ascii=False, separators=(",", ":")),
                          encoding="utf-8")
    related = write_related()
    print(f"words={len(words)} bytes={OUT.stat().st_size} details={len(details)} "
          f"detail_bytes={DETAIL_OUT.stat().st_size} cached={merged} related={related} "
          f"relate_bytes={RELATE_OUT.stat().st_size}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
