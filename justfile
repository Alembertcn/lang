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
#
# Windows：windows-shell 走 tools/win-bash.cmd（Git Bash），避开 System32 的 WSL bash。

set shell := ["bash", "-cu"]
# 直接调 Git Bash 包装器（不要套 cmd /C，否则 -cu 与配方参数会被丢掉）
set windows-shell := ["tools\\win-bash.cmd", "-cu"]

default:
    @just --list

# 把手机回环转到本机媒体 mock。mock 本身用 tools/start_media_mock.sh 起。
reverse:
    bash tools/just_reverse.sh

# 安装到已连接的 Android 手机并打开 page
android page="":
    bash tools/just_android.sh "{{page}}"

# 安装到已连接的 iPhone（否则已启动的模拟器）并打开 page
ios page="":
    bash tools/just_ios.sh "{{page}}"
