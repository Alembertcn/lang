#!/usr/bin/env bash
# Download Release-hosted build inputs. See ASSETS.md.
set -euo pipefail
cd "$(dirname "$0")/.."

BASE_URL="${MOJI_ASSETS_BASE_URL:-https://github.com/Alembertcn/lang/releases/download/assets-v1}"
WORDBOOK_DEST="composeApp/src/commonMain/composeResources/files/wordbook.db"

fetch() {
  local name="$1" dest="$2"
  echo "-> $name"
  mkdir -p "$(dirname "$dest")"
  curl -L --fail --progress-bar -o "$dest.tmp" "$BASE_URL/$name"
  mv "$dest.tmp" "$dest"
}

# 1) Word book (required for lookup)
if [ ! -f "$WORDBOOK_DEST" ]; then
  fetch "wordbook.db.zip" "/tmp/moji-wordbook.db.zip"
  unzip -o -q "/tmp/moji-wordbook.db.zip" -d "$(dirname "$WORDBOOK_DEST")"
  echo "wordbook.db ready"
else
  echo "skip wordbook.db (exists)"
fi

# 2) Book epub sources (optional; only if oss/books empty)
if [ ! -d reference/moji-api/oss/books ] || [ -z "$(ls -A reference/moji-api/oss/books 2>/dev/null || true)" ]; then
  if curl -L --fail -s -o /tmp/moji-books-oss.zip "$BASE_URL/moji-books-oss.zip" 2>/dev/null; then
    mkdir -p reference/moji-api/oss
    unzip -o -q /tmp/moji-books-oss.zip -d reference/moji-api/oss
    echo "book epub sources ready"
  else
    echo "skip book sources (no moji-books-oss.zip on Release; use fetch_moji_books.py)"
  fi
else
  echo "skip book sources (exist)"
fi

# 3) Original APK for reverse (optional; base.apk + splits)
APK_DEST="reference/moji-read/base.apk"
if [ ! -f "$APK_DEST" ]; then
  if curl -L --fail -s -o /tmp/moji-reverse-apk.zip "$BASE_URL/reverse-apk.zip" 2>/dev/null; then
    mkdir -p reference/moji-read
    unzip -o -q /tmp/moji-reverse-apk.zip -d reference/moji-read
    echo "reverse APK ready ($APK_DEST)"
  else
    echo "skip reverse APK (no reverse-apk.zip on Release)"
  fi
else
  echo "skip reverse APK (exists)"
fi

echo
echo "next:"
echo "  python3 tools/unpack_moji_books.py"
echo "  python3 tools/build_article_assets.py"
echo "  python3 tools/fetch_find_assets.py"
echo "  ./gradlew :composeApp:assembleDebug"
echo "media: tools/sync_lang_media_to_nas.sh  or  tools/start_media_mock.sh"
