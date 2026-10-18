#!/usr/bin/env bash
# Pack local heavy assets into dist/release/*.zip for GitHub Release upload.
# See ASSETS.md.
set -euo pipefail
cd "$(dirname "$0")/.."

STAGING="${RELEASE_STAGING:-dist/release-staging}"
OUT="${RELEASE_OUT:-dist/release}"
mkdir -p "$STAGING" "$OUT"

copy_if() {
  local src="$1" dest="$2"
  if [ -f "$src" ]; then
    cp -f "$src" "$dest"
    echo "staged $(basename "$dest")"
  else
    echo "skip missing $src" >&2
  fi
}

copy_if composeApp/src/commonMain/composeResources/files/wordbook.db "$STAGING/wordbook.db"
copy_if content/mojidict/dictionary.db "$STAGING/dictionary.db"
copy_if reference/dict/core-mojidict-sc-20241009.realm "$STAGING/core-mojidict-sc-20241009.realm"
copy_if reference/moji-read/base.apk "$STAGING/base.apk"
shopt -s nullglob
for f in reference/moji-read/split_config.*.apk; do
  cp -f "$f" "$STAGING/$(basename "$f")"
done
shopt -u nullglob

pack() {
  local zipname="$1"; shift
  local -a files=()
  local f
  for f in "$@"; do
    [ -f "$STAGING/$f" ] && files+=("$f")
  done
  if [ "${#files[@]}" -eq 0 ]; then
    echo "skip $zipname (no inputs)" >&2
    return 0
  fi
  rm -f "$OUT/$zipname"
  (cd "$STAGING" && zip -q -9 "$OLDPWD/$OUT/$zipname" "${files[@]}")
  echo "wrote $OUT/$zipname ($(du -h "$OUT/$zipname" | awk '{print $1}'))"
}

pack wordbook.db.zip wordbook.db
pack reverse-apk.zip base.apk \
  split_config.arm64_v8a.apk split_config.xxhdpi.apk \
  split_config.zh.apk split_config.en.apk split_config.ru.apk
pack dict-source.zip dictionary.db core-mojidict-sc-20241009.realm

echo
echo "upload: gh release create assets-v1 $OUT/*.zip -t assets-v1 -n 'offline assets v1'"
