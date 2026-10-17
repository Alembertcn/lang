#!/usr/bin/env python3
"""Pull the Study-tab APIs into reference/moji-api/reader.

StudyFragment (base.apk 3.4.9) shows:
  本月学习 / 小时      <- cloud function `readStats-articleMonthlyStats` (P4.H0)
  计划                <- GET /v1/readPlan/mine            (responses/readPlan.mine.json)
  订阅                <- cloud function `column-mySubscribed` (reader/column-mySubscribed.json)

Only the missing piece (monthly stats) is fetched here; the other two already
live in the captured cache.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import fetch_moji_articles as articles  # noqa: E402
import fetch_moji_plans as moji  # noqa: E402

OUT = moji.API_DIR / "reader" / "readStats-articleMonthlyStats.json"


def main() -> None:
    env = moji.load_env(moji.ENV_PATH)
    account = env.get("MOJI_ACCOUNT", "")
    password = env.get("MOJI_PASSWORD", "")
    if not account or not password:
        sys.exit("请在 .env 填写 MOJI_ACCOUNT 和 MOJI_PASSWORD。")
    token = moji.login(account, password)
    payload = articles.cloud("readStats-articleMonthlyStats", token, {})
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"ok {OUT.relative_to(moji.API_DIR)}")


if __name__ == "__main__":
    main()
