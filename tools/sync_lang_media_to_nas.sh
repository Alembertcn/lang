#!/usr/bin/env bash
# Rsync reference/moji-api/oss → NAS Caddy static root …/res/lang/
# Public URL: https://static.ailuo.fun/lang/…
#
# SSH host defaults to svr-a (see ~/.ssh/config). Override with NAS_SSH / NAS_LANG_DIR.
set -euo pipefail
cd "$(dirname "$0")/.."

SSH_HOST="${NAS_SSH:-svr-a}"
REMOTE_DIR="${NAS_LANG_DIR:-/volume1/docker/caddy/res/lang}"
SRC="reference/moji-api/oss/"
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

echo "rsync $SRC → ${SSH_HOST}:$REMOTE_DIR/"
ssh -o BatchMode=yes "$SSH_HOST" "mkdir -p '$REMOTE_DIR'"
rsync -a --info=progress2 --exclude '.DS_Store' --exclude 'missing.txt' \
  "$SRC" "${SSH_HOST}:$REMOTE_DIR/"

if [ "$APPLY_CADDY" -eq 1 ]; then
  echo "patch Caddyfile for /lang/* …"
  ssh -o BatchMode=yes "$SSH_HOST" 'python3 -' <<'PY'
from pathlib import Path
p = Path("/volume1/docker/caddy/Caddyfile")
text = p.read_text(encoding="utf-8")
old = "@cdn_res path /imgapp/* /voice/* /app/* /web/* /miniprogram/* /dota-pa/*"
new = "@cdn_res path /imgapp/* /voice/* /app/* /web/* /miniprogram/* /dota-pa/* /lang/*"
if "/lang/*" in text:
    print("Caddyfile already has /lang/*")
elif old not in text:
    raise SystemExit("Caddyfile @cdn_res line not found; patch manually")
else:
    p.write_text(text.replace(old, new, 1), encoding="utf-8")
    print("patched @cdn_res")
PY
  ssh -o BatchMode=yes "$SSH_HOST" 'cd /volume1/docker/caddy && docker compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile 2>/dev/null || docker compose restart caddy'
fi

echo
echo "verify: curl -sI https://static.ailuo.fun/lang/tts/sample/f002.mp3 | head -5"
echo "app:    MOCK_MEDIA_BASE=https://static.ailuo.fun/lang"
