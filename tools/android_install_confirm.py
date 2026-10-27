#!/usr/bin/env python3
"""录制 / 重放 Android 安装确认点击（荣耀等需「继续 / 仍要安装」的机型）。

经验（AGENTS.md「零之五」）：固定流程点击要轻量——
  getevent 按机型录坐标 → 装前挂 logcat 等出现 → 短等 + 固定间隔 input tap。
  重放路径不要 uiautomator dump / dumpsys window 轮询。

用法：
  # 第一次：启动安装并录触摸，你在手机上点完确认流程
  python3 tools/android_install_confirm.py record [apk]

  # 之后：有 profile 就边装边重放；没有则只装并提示去 record
  python3 tools/android_install_confirm.py install [apk]

  # 只重放已录路径（调试）
  python3 tools/android_install_confirm.py replay

  # 看当前机型与 profile 是否存在
  python3 tools/android_install_confirm.py info

环境变量：ANDROID_SERIAL 指定设备。
profile 落在 tools/android_install_profiles/<brand>_<model>.json
"""

from __future__ import annotations

import argparse
import json
import os
import re
import signal
import subprocess
import sys
import threading
import time
from pathlib import Path

# 被 just / 管道调用时也能立刻看到提示
try:
    sys.stdout.reconfigure(line_buffering=True)  # type: ignore[attr-defined]
    sys.stderr.reconfigure(line_buffering=True)  # type: ignore[attr-defined]
except Exception:
    pass

ROOT = Path(__file__).resolve().parents[1]
PROFILES = Path(__file__).resolve().parent / "android_install_profiles"
DEFAULT_APK = ROOT / "composeApp/build/outputs/apk/debug/composeApp-debug.apk"
ADB = os.environ.get("ADB") or "adb"


def run(args: list[str], **kw) -> subprocess.CompletedProcess:
    return subprocess.run(args, text=True, capture_output=True, **kw)


def adb(serial: str, *args: str, **kw) -> subprocess.CompletedProcess:
    return run([ADB, "-s", serial, *args], **kw)


def pick_serial(cli: str | None) -> str:
    if cli:
        return cli
    env = os.environ.get("ANDROID_SERIAL")
    if env:
        return env
    out = run([ADB, "devices"]).stdout
    for line in out.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            return parts[0]
    sys.exit("没有连着的 Android 手机（adb devices）")


def device_props(serial: str) -> dict:
    def prop(name: str) -> str:
        return adb(serial, "shell", "getprop", name).stdout.strip()

    size = adb(serial, "shell", "wm", "size").stdout
    m = re.search(r"(\d+)x(\d+)", size)
    w, h = (int(m.group(1)), int(m.group(2))) if m else (0, 0)
    return {
        "serial": serial,
        "brand": prop("ro.product.brand") or prop("ro.product.manufacturer") or "unknown",
        "model": prop("ro.product.model") or prop("ro.product.device") or "unknown",
        "screen_w": w,
        "screen_h": h,
    }


def profile_path(props: dict) -> Path:
    brand = re.sub(r"[^\w.-]+", "_", props["brand"])
    model = re.sub(r"[^\w.-]+", "_", props["model"])
    return PROFILES / f"{brand}_{model}.json"


def load_profile(props: dict) -> dict | None:
    path = profile_path(props)
    if not path.is_file():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def save_profile(props: dict, taps: list[dict], touch_device: str | None) -> Path:
    PROFILES.mkdir(parents=True, exist_ok=True)
    path = profile_path(props)
    data = {
        "brand": props["brand"],
        "model": props["model"],
        "serial_recorded_on": props["serial"],
        "screen": {"width": props["screen_w"], "height": props["screen_h"]},
        "touch_device": touch_device,
        "taps": taps,
        "recorded_at": time.strftime("%Y-%m-%dT%H:%M:%S"),
    }
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return path


# ---------- touch device discovery & getevent parsing ----------

ABS_MT_POSITION_X = "ABS_MT_POSITION_X"
ABS_MT_POSITION_Y = "ABS_MT_POSITION_Y"
ABS_X = "ABS_X"
ABS_Y = "ABS_Y"


def list_touch_devices(serial: str) -> list[dict]:
    """Parse `getevent -pl` for DIRECT touch devices with ABS ranges."""
    out = adb(serial, "shell", "getevent", "-pl").stdout
    devices: list[dict] = []
    cur: dict | None = None
    for line in out.splitlines():
        m = re.match(r"add device \d+: (/dev/input/event\d+)", line)
        if m:
            if cur and cur.get("max_x") is not None:
                devices.append(cur)
            cur = {
                "path": m.group(1),
                "name": "",
                "direct": False,
                "max_x": None,
                "max_y": None,
                "mt_max_x": None,
                "mt_max_y": None,
            }
            continue
        if cur is None:
            continue
        if "name:" in line:
            cur["name"] = line.split("name:", 1)[1].strip().strip('"')
        if "INPUT_PROP_DIRECT" in line:
            cur["direct"] = True
        # ABS_MT_POSITION_X     : value 0, min 0, max 8479
        mm = re.search(
            r"(ABS_MT_POSITION_X|ABS_MT_POSITION_Y|ABS_X|ABS_Y)\s*:\s*.*max\s+(-?\d+)",
            line,
        )
        if mm:
            key, mx = mm.group(1), int(mm.group(2))
            if mx < 0:
                continue
            if key == ABS_MT_POSITION_X:
                cur["mt_max_x"] = mx
            elif key == ABS_MT_POSITION_Y:
                cur["mt_max_y"] = mx
            elif key == ABS_X:
                cur["max_x"] = mx
            elif key == ABS_Y:
                cur["max_y"] = mx
    if cur and cur.get("max_x") is not None:
        devices.append(cur)

    # Prefer DIRECT devices whose ABS_X≈screen width (inner panel on foldables).
    return [d for d in devices if d.get("direct") and d.get("max_x") and d.get("max_y")]


def choose_touch_device(serial: str, props: dict) -> dict | None:
    devices = list_touch_devices(serial)
    if not devices:
        return None
    sw, sh = props["screen_w"], props["screen_h"]

    def score(d: dict) -> tuple:
        # closer ABS size to wm size is better; prefer having MT axes
        dx = abs((d["max_x"] or 0) - max(sw - 1, 0))
        dy = abs((d["max_y"] or 0) - max(sh - 1, 0))
        has_mt = 0 if d.get("mt_max_x") else 1
        return (dx + dy, has_mt, d["path"])

    return sorted(devices, key=score)[0]


# getevent -lt 指定设备时通常不带 /dev/input/eventN: 前缀
_GETEVENT_RE = re.compile(
    r"\[\s*([\d.]+)\]\s+(?:(/dev/input/event\d+):\s+)?(\S+)\s+(\S+)\s+(\S+)"
)

# 无 -l 时的数字码
_EV_ABS, _EV_KEY, _EV_SYN = "0003", "0001", "0000"
_CODE = {
    "0035": ABS_MT_POSITION_X,
    "0036": ABS_MT_POSITION_Y,
    "0039": "ABS_MT_TRACKING_ID",
    "0000": ABS_X,
    "0001": ABS_Y,
    "014a": "BTN_TOUCH",
}


def _norm_code(etype: str, ecode: str) -> tuple[str, str]:
    """Map numeric getevent codes to symbolic names when needed."""
    if etype in ("EV_ABS", "EV_KEY", "EV_SYN"):
        return etype, ecode
    et = {"0003": "EV_ABS", "0001": "EV_KEY", "0000": "EV_SYN"}.get(etype.lower(), etype)
    if et == "EV_ABS":
        return et, _CODE.get(ecode.lower(), ecode)
    if et == "EV_KEY":
        return et, _CODE.get(ecode.lower(), ecode)
    return et, ecode


def parse_getevent_taps(
    log: str,
    device: dict,
    screen_w: int,
    screen_h: int,
) -> list[dict]:
    """Convert getevent -lt log into screen-space taps (down→up = one tap)."""
    path = device["path"]
    abs_max_x = device.get("mt_max_x") or device["max_x"]
    abs_max_y = device.get("mt_max_y") or device["max_y"]
    # Prefer ABS_* when they already match screen (Honor wrapper).
    use_abs = (
        device.get("max_x")
        and device.get("max_y")
        and abs(device["max_x"] - (screen_w - 1)) <= 2
        and abs(device["max_y"] - (screen_h - 1)) <= 2
    )

    mt_x = mt_y = None
    abs_x = abs_y = None
    down_t = None
    finger_down = False
    taps: list[dict] = []
    t0 = None

    def to_screen(raw_x: int, raw_y: int, max_x: int, max_y: int) -> tuple[int, int]:
        sx = int(round(raw_x * (screen_w - 1) / max_x)) if max_x else raw_x
        sy = int(round(raw_y * (screen_h - 1) / max_y)) if max_y else raw_y
        return max(0, min(screen_w - 1, sx)), max(0, min(screen_h - 1, sy))

    def emit(ts: float) -> None:
        nonlocal down_t
        if use_abs and abs_x is not None and abs_y is not None:
            sx, sy = abs_x, abs_y
        elif mt_x is not None and mt_y is not None:
            sx, sy = to_screen(mt_x, mt_y, abs_max_x, abs_max_y)
        elif abs_x is not None and abs_y is not None:
            sx, sy = abs_x, abs_y
        else:
            down_t = None
            return
        taps.append(
            {
                "t_ms": int(round(((down_t if down_t is not None else ts) - t0) * 1000)),
                "x": int(sx),
                "y": int(sy),
            }
        )
        down_t = None

    for line in log.splitlines():
        m = _GETEVENT_RE.match(line.strip())
        if not m:
            continue
        ts_s, dev, etype, ecode, val_s = m.groups()
        # 指定单设备录制时行内无 path；多设备时要过滤
        if dev and path and dev != path:
            continue
        ts = float(ts_s)
        if t0 is None:
            t0 = ts
        raw = val_s.strip().upper()
        if raw in ("DOWN", "TRUE"):
            val = 1
        elif raw in ("UP", "FALSE"):
            val = 0
        else:
            try:
                val = int(val_s, 16)
            except ValueError:
                try:
                    val = int(val_s)
                except ValueError:
                    continue

        etype, ecode = _norm_code(etype, ecode)

        if etype == "EV_ABS":
            if ecode == ABS_MT_POSITION_X:
                mt_x = val
            elif ecode == ABS_MT_POSITION_Y:
                mt_y = val
            elif ecode == ABS_X:
                abs_x = val
            elif ecode == ABS_Y:
                abs_y = val
            elif ecode == "ABS_MT_TRACKING_ID":
                # -1 / 0xffffffff = lift
                if val in (-1, 0xFFFFFFFF) and finger_down:
                    emit(ts)
                    finger_down = False
                elif val >= 0:
                    finger_down = True
                    if down_t is None:
                        down_t = ts
        elif etype == "EV_KEY" and ecode == "BTN_TOUCH":
            if val == 1:
                finger_down = True
                down_t = ts
            elif val == 0 and finger_down:
                emit(ts)
                finger_down = False

    # Dedup rapid double-fires within 80ms at nearly same point
    cleaned: list[dict] = []
    for tap_item in taps:
        if cleaned:
            prev = cleaned[-1]
            if (
                abs(tap_item["t_ms"] - prev["t_ms"]) < 80
                and abs(tap_item["x"] - prev["x"]) < 20
                and abs(tap_item["y"] - prev["y"]) < 20
            ):
                continue
        cleaned.append(tap_item)
    # Normalize times so first tap is ~400ms after replay start
    if cleaned:
        base = cleaned[0]["t_ms"]
        for tap_item in cleaned:
            tap_item["t_ms"] = max(0, tap_item["t_ms"] - base) + 400
    return cleaned


def start_getevent(serial: str, device_path: str) -> tuple[subprocess.Popen, list[str]]:
    lines: list[str] = []
    proc = subprocess.Popen(
        [ADB, "-s", serial, "shell", "getevent", "-lt", device_path],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
    )

    def reader() -> None:
        assert proc.stdout is not None
        for line in proc.stdout:
            lines.append(line.rstrip("\n"))

    threading.Thread(target=reader, daemon=True).start()
    return proc, lines


def tap(serial: str, x: int, y: int) -> None:
    adb(serial, "shell", "input", "tap", str(x), str(y))


def ui_texts(serial: str) -> str:
    adb(serial, "shell", "uiautomator", "dump", "/sdcard/moji_uidump.xml")
    xml = adb(serial, "shell", "cat", "/sdcard/moji_uidump.xml").stdout
    return " ".join(re.findall(r'text="([^"]+)"', xml))


def start_installer_watch(serial: str) -> tuple[threading.Event, subprocess.Popen]:
    """在 adb install 之前启动：用 logcat 等 PackageInstaller（荣耀上 dumpsys window 单次要数秒）。"""
    ready = threading.Event()
    proc = subprocess.Popen(
        [ADB, "-s", serial, "logcat", "-v", "brief", "-T", "1"],
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        bufsize=1,
    )

    def reader() -> None:
        assert proc.stdout is not None
        for line in proc.stdout:
            if ready.is_set():
                break
            low = line.lower()
            if "packageinstaller" not in low:
                continue
            # START / Displayed / RESUMED / cmp=...PackageInstaller...
            if any(
                k in low
                for k in (
                    " start ",
                    "startproc",
                    "displayed",
                    "resumed",
                    "cmp=",
                    "packageinstalleractivity",
                    "safemode",
                )
            ):
                ready.set()
                break

    threading.Thread(target=reader, daemon=True).start()
    return ready, proc


def wait_installer(
    serial: str,
    ready: threading.Event | None = None,
    timeout_s: float = 20.0,
) -> bool:
    """等安装确认界面。优先用预先挂好的 logcat Event；否则扫 logcat -d 缓冲。"""
    if ready is not None:
        if ready.wait(timeout=timeout_s):
            print("已出现 PackageInstaller (logcat)")
            time.sleep(0.08)
            return True
        print("logcat 等待超时，尝试扫缓冲…", file=sys.stderr)

    deadline = time.monotonic() + min(timeout_s, 5.0)
    while time.monotonic() < deadline:
        # logcat -d 通常比 dumpsys window 快一个数量级
        out = adb(
            serial,
            "shell",
            "sh",
            "-c",
            "logcat -d -t 80 2>/dev/null | grep -i packageinstaller | tail -5",
        ).stdout
        if "packageinstaller" in out.lower():
            print("已出现 PackageInstaller (logcat -d)")
            time.sleep(0.08)
            return True
        time.sleep(0.12)
    print("等待安装确认超时", file=sys.stderr)
    return False


def replay_taps(
    serial: str,
    taps: list[dict],
    *,
    ready: threading.Event | None = None,
    after_logcat_s: float = 0.8,
    step_gap_s: float = 0.9,
) -> None:
    """logcat 出现 PackageInstaller 后，固定间隔按坐标硬点（不做 uiautomator dump）。"""
    if not taps:
        print("profile 里没有 tap", file=sys.stderr)
        return
    if not wait_installer(serial, ready=ready):
        print("未等到安装确认，仍按坐标硬点…", file=sys.stderr)

    # 等弹窗可点（比 dump 便宜）；间隔用固定值，不读录制轴、不 dump
    if after_logcat_s > 0:
        time.sleep(after_logcat_s)
    print(
        f"重放 {len(taps)} 次点击（logcat后等 {after_logcat_s}s，步间隔 {step_gap_s}s，无 dump）…"
    )
    for i, tap_info in enumerate(taps, 1):
        if i > 1 and step_gap_s > 0:
            time.sleep(step_gap_s)
        x, y = int(tap_info["x"]), int(tap_info["y"])
        print(f"  [{i}/{len(taps)}] tap {x},{y}")
        tap(serial, x, y)
    time.sleep(0.2)


def do_install(serial: str, apk: Path) -> int:
    if not apk.is_file():
        sys.exit(f"找不到 apk: {apk}")
    print(f"adb install -r -t {apk}")
    p = subprocess.run(
        [ADB, "-s", serial, "install", "-r", "-t", str(apk)],
        text=True,
    )
    return p.returncode


def cmd_info(serial: str) -> int:
    props = device_props(serial)
    path = profile_path(props)
    touch = choose_touch_device(serial, props)
    print(f"设备  {props['brand']} {props['model']}  serial={props['serial']}")
    print(f"屏幕  {props['screen_w']}x{props['screen_h']}")
    if touch:
        print(f"触摸  {touch['path']} ({touch['name']}) ABS {touch['max_x']}x{touch['max_y']}")
    else:
        print("触摸  (未识别 DIRECT 设备)")
    if path.is_file():
        data = json.loads(path.read_text(encoding="utf-8"))
        print(f"profile 已有 {path.relative_to(ROOT)}  taps={len(data.get('taps', []))}")
    else:
        print(f"profile 无 → 先跑: python3 tools/android_install_confirm.py record")
    return 0


def cmd_record(serial: str, apk: Path) -> int:
    props = device_props(serial)
    touch = choose_touch_device(serial, props)
    if not touch:
        sys.exit("找不到可用触摸设备，无法录制")

    print("=" * 60)
    print(f"录制安装确认：{props['brand']} {props['model']}")
    print(f"触摸设备：{touch['path']} ({touch['name']})")
    print("先卸载旧包以强制弹出系统确认，然后请在手机上点完：")
    print("  继续 → 勾选风险 → 继续安装 → 仍要安装 …")
    print("装完后脚本会自动保存点击路径。")
    print("=" * 60)

    # 强制再走一遍确认弹窗（已装过时 adb install 可能秒成功且无弹窗）
    adb(serial, "shell", "input", "keyevent", "3")
    adb(serial, "uninstall", "com.moji.offline")
    time.sleep(0.4)

    ge_proc, ge_lines = start_getevent(serial, touch["path"])
    time.sleep(0.4)

    code = 1
    try:
        code = do_install(serial, apk)
    finally:
        # 再留一会收尾点击
        time.sleep(0.8)
        ge_proc.send_signal(signal.SIGTERM)
        try:
            ge_proc.wait(timeout=2)
        except subprocess.TimeoutExpired:
            ge_proc.kill()

    log = "\n".join(ge_lines)
    # 调试：解析失败时留一份原始 log
    debug_log = PROFILES / "_last_getevent.log"
    PROFILES.mkdir(parents=True, exist_ok=True)
    debug_log.write_text(log + "\n", encoding="utf-8")

    taps = parse_getevent_taps(log, touch, props["screen_w"], props["screen_h"])
    print(f"捕获到 {len(taps)} 次点击（getevent 行数 {len(ge_lines)}）")
    for t in taps:
        print(f"  t={t['t_ms']}ms  ({t['x']},{t['y']})")

    if code != 0:
        print(f"安装失败 exit={code}，仍保存已录点击供检查。", file=sys.stderr)
    if not taps:
        print(
            "没有录到点击。请确认：手机已解锁、弹窗出现后用手指点。\n"
            f"原始 getevent 已保存：{debug_log.relative_to(ROOT)}",
            file=sys.stderr,
        )
        if ge_lines:
            print("--- getevent 样例 ---", file=sys.stderr)
            for line in ge_lines[:8]:
                print(line, file=sys.stderr)
        return code or 2

    path = save_profile(props, taps, touch["path"])
    print(f"已写入 {path.relative_to(ROOT)}")
    return code


def cmd_replay(serial: str) -> int:
    props = device_props(serial)
    data = load_profile(props)
    if not data or not data.get("taps"):
        sys.exit(f"没有 profile：{profile_path(props)}")
    replay_taps(serial, data["taps"])
    return 0


def cmd_install(serial: str, apk: Path) -> int:
    props = device_props(serial)
    data = load_profile(props)
    stop = threading.Event()
    ready: threading.Event | None = None
    logcat_proc: subprocess.Popen | None = None

    if data and data.get("taps"):
        # 必须在 adb install 之前挂上 logcat，否则弹窗已出现时 dumpsys/后挂 logcat 会漏事件还巨慢
        ready, logcat_proc = start_installer_watch(serial)
        time.sleep(0.05)
        print(f"使用 profile {profile_path(props).name} 重放安装确认")
    else:
        print(
            f"无安装确认 profile（{profile_path(props).name}）。"
            "若一直卡在系统确认，请在手机上点，或先：\n"
            "  python3 tools/android_install_confirm.py record",
            file=sys.stderr,
        )

    def watcher() -> None:
        if not data or not data.get("taps"):
            return
        if stop.is_set():
            return
        replay_taps(serial, data["taps"], ready=ready)

    th = threading.Thread(target=watcher, daemon=True)
    th.start()
    try:
        code = do_install(serial, apk)
    finally:
        stop.set()
        if logcat_proc is not None:
            logcat_proc.kill()
    th.join(timeout=60)
    return code


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("-s", "--serial", help="adb serial")
    sub = ap.add_subparsers(dest="cmd", required=True)

    p_info = sub.add_parser("info", help="查看设备与 profile")
    p_rec = sub.add_parser("record", help="安装并录制你的点击")
    p_rec.add_argument("apk", nargs="?", type=Path, default=DEFAULT_APK)
    p_rep = sub.add_parser("replay", help="只重放已录点击")
    p_ins = sub.add_parser("install", help="安装；有 profile 则自动重放")
    p_ins.add_argument("apk", nargs="?", type=Path, default=DEFAULT_APK)

    args = ap.parse_args()
    serial = pick_serial(args.serial)

    if args.cmd == "info":
        return cmd_info(serial)
    if args.cmd == "record":
        return cmd_record(serial, args.apk)
    if args.cmd == "replay":
        return cmd_replay(serial)
    if args.cmd == "install":
        return cmd_install(serial, args.apk)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
