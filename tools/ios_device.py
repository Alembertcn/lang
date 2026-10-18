#!/usr/bin/env python3
"""Pick a connected iPhone from `devicectl list devices --json-output`.

  ios_device.py udid /tmp/moji-ios-devices.json
      prints the hardware UDID of the first phone whose tunnel is up

  ios_device.py explain /tmp/moji-ios-devices.json
      prints paired-but-offline names to stderr
"""

import json
import sys

READY = {"connected", "available"}


def devices(path: str):
    return json.load(open(path))["result"]["devices"]


def physical(devs):
    for dev in devs:
        hw = dev.get("hardwareProperties") or {}
        if hw.get("reality") == "physical" and hw.get("platform") == "iOS":
            yield dev, hw


def main() -> int:
    cmd, path = sys.argv[1], sys.argv[2]
    devs = devices(path)
    if cmd == "udid":
        for dev, hw in physical(devs):
            tunnel = (dev.get("connectionProperties") or {}).get("tunnelState") or ""
            if tunnel in READY and hw.get("udid"):
                print(hw["udid"])
                return 0
        return 2
    if cmd == "explain":
        names = []
        for dev, hw in physical(devs):
            name = (dev.get("deviceProperties") or {}).get("name") or hw.get("udid")
            names.append(name)
        if names:
            print("已配对但当前连不上：" + "、".join(names), file=sys.stderr)
        print(
            "没有可安装的 iPhone，也没有已启动的模拟器。请解锁并用数据线连接，或先启动模拟器后再跑 just ios。",
            file=sys.stderr,
        )
        return 0
    print(f"unknown command: {cmd}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
