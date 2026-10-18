#!/usr/bin/env bash
# Evaluate JS inside the on-device article WebView via the DevTools socket.
# Usage: tools/webview_eval.sh '<js expression>'
set -euo pipefail
SER=${SER:-A2AV024B08000258}
PID=$(adb -s "$SER" shell pidof com.moji.offline | tr -d '\r')
if [ -z "$PID" ]; then echo "app not running" >&2; exit 1; fi
adb -s "$SER" forward tcp:9222 "localabstract:webview_devtools_remote_$PID" >/dev/null
node "$(dirname "$0")/cdp_eval.js" "$1"
