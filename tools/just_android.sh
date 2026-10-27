#!/usr/bin/env bash
# just android [page] — 编译安装到已连接 Android 并打开页面
set -euo pipefail
cd "$(dirname "$0")/.."

# Windows 上 WindowsApps\python3 常是商店占位符；优先能真正跑起来的解释器。
run_python() {
  local c
  for c in python python3; do
    if command -v "$c" >/dev/null 2>&1 && "$c" -c "import sys" >/dev/null 2>&1; then
      "$c" "$@"
      return
    fi
  done
  echo "需要可用的 Python 3（python 或 python3）" >&2
  exit 1
}

canon() {
  case "$1" in
    ""|discover) echo "" ;;
    find|发现) echo discover ;;
    reader|阅读|阅读器) echo reader ;;
    study|学习) echo study ;;
    mine|我的) echo mine ;;
    settings|setting|设置) echo settings ;;
    history|历史|最近阅读) echo history ;;
    favorites|fav|收藏) echo favorites ;;
    theme|主题) echo theme ;;
    *)
      echo "不认识的页面: $1" >&2
      echo "可用：discover reader study mine settings history favorites theme" >&2
      exit 1
      ;;
  esac
}
page="$(canon "${1:-}")"
serial="${ANDROID_SERIAL:-}"
if [[ -z "$serial" ]]; then
  serial="$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
fi
if [[ -z "$serial" ]]; then
  echo "没有连着的 Android 手机（adb devices）" >&2
  exit 1
fi
echo "Android 设备 $serial ，页面 ${page:-发现}"
./gradlew :composeApp:assembleDebug
# 有录好的安装确认 profile 则边装边重放；没有则普通 install（可能卡在系统确认）
run_python tools/android_install_confirm.py -s "$serial" install \
  composeApp/build/outputs/apk/debug/composeApp-debug.apk
adb -s "$serial" shell am force-stop com.moji.offline
if [[ -n "$page" ]]; then
  adb -s "$serial" shell am start -n com.moji.offline/.MainActivity --es page "$page"
else
  adb -s "$serial" shell am start -n com.moji.offline/.MainActivity
fi
