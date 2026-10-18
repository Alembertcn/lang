#!/usr/bin/env bash
# Start the local media mock (persistent, via screen) and wire adb reverse so the
# phone can reach it without depending on Wi-Fi / VPN state.
#
#   media mock (host):  0.0.0.0:8765  ->  reference/moji-api/oss
#   phone side:         127.0.0.1:18080 -> host 8765 (adb reverse, USB)
#
# Phone build config: MOCK_MEDIA_BASE=http://127.0.0.1:18080 in local.properties.
set -euo pipefail
cd "$(dirname "$0")/.."

if screen -ls 2>/dev/null | grep -q "moji-mock"; then
  echo "media mock already running (screen -r moji-mock to inspect)"
else
  screen -dmS moji-mock bash -c "cd \"$(pwd)\" && exec python3 -u -m http.server 8765 --directory reference/moji-api/oss --bind 0.0.0.0"
  sleep 1
  echo "media mock started on :8765 (screen session: moji-mock)"
fi

if command -v adb >/dev/null 2>&1; then
  if adb reverse tcp:18080 tcp:8765 2>/dev/null; then
    echo "adb reverse ready: phone 127.0.0.1:18080 -> host 8765"
  else
    echo "adb reverse skipped (no device?)"
  fi
fi

echo
echo "verify from host:   curl -I http://127.0.0.1:8765/article/audio/"
echo "stop mock:          screen -S moji-mock -X quit"
