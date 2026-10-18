# 安装并打开本机高仿。page 可省略（停在发现页）。
#
#   just android
#   just android settings
#   just ios mine
#
# page：discover / reader / study / mine / settings / history / favorites / theme
# 中文别名：发现、阅读器、学习、我的、设置、历史、收藏、主题
# 多设备时用 ANDROID_SERIAL、IOS_UDID 指定。
#
#   just reverse
# 手机 127.0.0.1:18080 → 本机媒体 mock :8765

default:
    @just --list

# 把手机回环转到本机媒体 mock。mock 本身用 tools/start_media_mock.sh 起。
reverse:
    #!/usr/bin/env bash
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

# 安装到已连接的 Android 手机并打开 page
android page="":
    #!/usr/bin/env bash
    set -euo pipefail
    cd "{{justfile_directory()}}"
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
    page="$(canon "{{page}}")"
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
    python3 tools/android_install_confirm.py -s "$serial" install \
        composeApp/build/outputs/apk/debug/composeApp-debug.apk
    adb -s "$serial" shell am force-stop com.moji.offline
    if [[ -n "$page" ]]; then
        adb -s "$serial" shell am start -n com.moji.offline/.MainActivity --es page "$page"
    else
        adb -s "$serial" shell am start -n com.moji.offline/.MainActivity
    fi

# 安装到已连接的 iPhone（否则已启动的模拟器）并打开 page
ios page="":
    #!/usr/bin/env bash
    set -euo pipefail
    cd "{{justfile_directory()}}"
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
    page="$(canon "{{page}}")"
    udid="${IOS_UDID:-}"
    sdk_dir="Debug-iphoneos"
    # xctrace 会把已配对但没连上的手机也列进 Devices。以 devicectl 的 tunnel 为准。
    xcrun devicectl list devices --json-output /tmp/moji-ios-devices.json >/dev/null
    if [[ -z "$udid" ]]; then
        udid="$(python3 tools/ios_device.py udid /tmp/moji-ios-devices.json || true)"
    fi
    if [[ -n "$udid" ]]; then
        dest="platform=iOS,id=$udid"
        sdk_dir="Debug-iphoneos"
        echo "iPhone $udid ，页面 ${page:-发现}"
    else
        udid="$(xcrun simctl list devices booted | grep -oE '[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}' | head -1 || true)"
        if [[ -z "$udid" ]]; then
            python3 tools/ios_device.py explain /tmp/moji-ios-devices.json >&2
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
