#!/usr/bin/env bash
# just ios [page] — 编译安装到已连接 iPhone（否则已启动的模拟器）并打开页面
set -euo pipefail
cd "$(dirname "$0")/.."

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
udid="${IOS_UDID:-}"
sdk_dir="Debug-iphoneos"
# xctrace 会把已配对但没连上的手机也列进 Devices。以 devicectl 的 tunnel 为准。
xcrun devicectl list devices --json-output /tmp/moji-ios-devices.json >/dev/null
if [[ -z "$udid" ]]; then
  udid="$(run_python tools/ios_device.py udid /tmp/moji-ios-devices.json || true)"
fi
if [[ -n "$udid" ]]; then
  dest="platform=iOS,id=$udid"
  sdk_dir="Debug-iphoneos"
  echo "iPhone $udid ，页面 ${page:-发现}"
else
  udid="$(xcrun simctl list devices booted | grep -oE '[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}' | head -1 || true)"
  if [[ -z "$udid" ]]; then
    run_python tools/ios_device.py explain /tmp/moji-ios-devices.json >&2
    exit 1
  fi
  dest="platform=iOS Simulator,id=$udid"
  sdk_dir="Debug-iphonesimulator"
  echo "iOS 模拟器 $udid ，页面 ${page:-发现}"
fi
xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -destination "$dest" \
  -derivedDataPath iosApp/build \
  -allowProvisioningUpdates \
  build
app="$(find "iosApp/build/Build/Products/$sdk_dir" -maxdepth 1 -name '*.app' | head -1)"
if [[ -z "$app" ]]; then
  echo "没找到编出来的 .app" >&2
  exit 1
fi
if [[ "$sdk_dir" == "Debug-iphoneos" ]]; then
  xcrun devicectl device install app --device "$udid" "$app"
  if [[ -n "$page" ]]; then
    xcrun devicectl device process launch --device "$udid" com.moji.offline -- --page "$page"
  else
    xcrun devicectl device process launch --device "$udid" com.moji.offline
  fi
else
  xcrun simctl install "$udid" "$app"
  if [[ -n "$page" ]]; then
    xcrun simctl launch "$udid" com.moji.offline --page "$page"
  else
    xcrun simctl launch "$udid" com.moji.offline
  fi
fi
