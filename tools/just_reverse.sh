#!/usr/bin/env bash
# just reverse — 手机 127.0.0.1:18080 → 本机媒体 mock :8765
set -euo pipefail
serial="${ANDROID_SERIAL:-}"
if [[ -z "$serial" ]]; then
  serial="$(adb devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ {print $1; exit}')"
fi
if [[ -z "$serial" ]]; then
  serial="$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
fi
if [[ -z "$serial" ]]; then
  echo "没有连着的 Android 手机（adb devices）" >&2
  exit 1
fi
adb -s "$serial" reverse tcp:18080 tcp:8765
echo "$serial  127.0.0.1:18080 → 本机 :8765"
adb -s "$serial" reverse --list
