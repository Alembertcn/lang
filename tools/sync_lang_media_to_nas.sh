#!/usr/bin/env bash
# Sync reference/moji-api/oss → NAS Caddy …/res/lang/
# Public: https://static.ailuo.fun/lang/…
#
# Ugreen NAS blocks rsync into arbitrary paths; use tar-over-ssh instead.
# SSH host: NAS_SSH (default svr-a). Remote dir: NAS_LANG_DIR.
set -euo pipefail
cd "$(dirname "$0")/.."

SSH_HOST="${NAS_SSH:-svr-a}"
REMOTE_DIR="${NAS_LANG_DIR:-/volume1/docker/caddy/res/lang}"
SRC="reference/moji-api/oss"
APPLY_CADDY=0

for arg in "$@"; do
  case "$arg" in
    --apply-caddy) APPLY_CADDY=1 ;;
    -h|--help)
      echo "usage: $0 [--apply-caddy]"
      exit 0
      ;;
  esac
done

if [ ! -d "$SRC" ]; then
  echo "missing $SRC" >&2
  exit 1
fi

if [ "$APPLY_CADDY" -eq 1 ]; then
  echo "patch Caddyfile for /lang/* …"
  ssh -o BatchMode=yes "$SSH_HOST" 'python3 -' <<'PY'
from pathlib import Path
p = Path("/volume1/docker/caddy/Caddyfile")
text = p.read_text(encoding="utf-8")
old = "@cdn_res path /imgapp/* /voice/* /app/* /web/* /miniprogram/* /dota-pa/*"
new = "@cdn_res path /imgapp/* /voice/* /app/* /web/* /miniprogram/* /dota-pa/* /lang/*"
if any("/lang/*" in line and "@cdn_res" in line for line in text.splitlines()):
    print("Caddyfile already has /lang/*")
elif old not in text:
    raise SystemExit("Caddyfile @cdn_res line not found; patch manually")
else:
    p.write_text(text.replace(old, new, 1), encoding="utf-8")
    print("patched @cdn_res")
PY
  ssh -o BatchMode=yes "$SSH_HOST" \
    'cd /volume1/docker/caddy && docker compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile'
fi

echo "tar-sync $SRC/ → ${SSH_HOST}:$REMOTE_DIR/  (may take a while for ~16GB)"
ssh -o BatchMode=yes "$SSH_HOST" "mkdir -p '$REMOTE_DIR'"
# Avoid AppleDouble xattrs / ._ files
export COPYFILE_DISABLE=1
tar -C "$SRC" \
  --exclude '.DS_Store' \
  --exclude '._*' \
  --exclude 'missing.txt' \
  -cf - . \
  | ssh -o BatchMode=yes "$SSH_HOST" "cd '$REMOTE_DIR' && tar xf -"

echo
echo "verify: curl -sI https://static.ailuo.fun/lang/tts/sample/f002.mp3 | head -5"
echo "app:    MOCK_MEDIA_BASE=https://static.ailuo.fun/lang"
