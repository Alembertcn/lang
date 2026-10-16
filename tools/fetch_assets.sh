#!/usr/bin/env bash
# Download release-hosted assets (dictionary db, book epub sources) and unpack
# them into the expected locations. See ASSETS.md.
set -euo pipefail
cd "$(dirname "$0")/.."

BASE_URL="${MOJI_ASSETS_BASE_URL:-https://github.com/<owner>/<repo>/releases/download/assets-v1}"

fetch() {
  local name="$1" dest="$2"
  echo "-> $name"
  mkdir -p "$(dirname "$dest")"
  curl -L --fail --progress-bar -o "$dest.tmp" "$BASE_URL/$name"
  mv "$dest.tmp" "$dest"
}

# 1) Dictionary database (64 MB)
if [ ! -f content/mojidict/dictionary.db ]; then
  fetch "mojidict-db.zip" "/tmp/mojidict-db.zip"
  mkdir -p content/mojidict
  unzip -o -q "/tmp/mojidict-db.zip" -d content/mojidict
  echo "dictionary.db ready"
else
  echo "skip dictionary.db (exists)"
fi

# 2) Book epub sources (small; optional when local copies exist)
if [ ! -d reference/moji-api/oss/books ] || [ -z "$(ls -A reference/moji-api/oss/books 2>/dev/null)" ]; then
  fetch "moji-books-oss.zip" "/tmp/moji-books-oss.zip"
  mkdir -p reference/moji-api/oss
  unzip -o -q "/tmp/moji-books-oss.zip" -d reference/moji-api/oss
  echo "book epub sources ready"
else
  echo "skip book sources (exist)"
fi

echo
echo "next: python3 tools/convert_moji_books.py && ./gradlew :composeApp:assembleDebug"
