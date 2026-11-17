"""Bounded, read-only CPU/RAM samples on the explicit Nokia development target.

Screen control is optional. USB-powered samples do not measure battery life.
The profiler never resets batterystats or changes charging/radio settings.
"""
import argparse
import base64
import datetime
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import struct
import time


def cpu_ticks(stat):
    fields = stat.rsplit(") ", 1)[1].split()
    return int(fields[11]) + int(fields[12])


def cpu_percent(first, last, hz):
    elapsed = last["uptime_seconds"] - first["uptime_seconds"]
    if elapsed <= 0 or first["pid"] != last["pid"]:
        return None
    return round(100 * (last["cpu_ticks"] - first["cpu_ticks"]) / hz / elapsed, 3)


def clock_ticks(auxv):
    # The reference handset is ARM32 little endian. AT_CLKTCK is supplied by
    # its running kernel, so measurements need no guessed CONFIG_HZ value.
    for kind, value in struct.iter_unpack("<II", auxv):
        if kind == 17 and value > 0:
            return value
    raise ValueError("Missing AT_CLKTCK in the target process auxiliary vector")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, choices=["695d"])
    parser.add_argument("--adb", type=Path)
    parser.add_argument("--label", required=True)
    parser.add_argument("--samples", type=int, default=4)
    parser.add_argument("--interval", type=float, default=15)
    parser.add_argument("--screen", choices=["keep", "awake", "asleep"], default="keep")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if not 2 <= args.samples <= 12 or not 1 <= args.interval <= 30:
        parser.error("use 2–12 samples, 1–30 seconds apart")
    if args.output.exists():
        parser.error("output already exists; choose a new measurement name")
    adb = str(args.adb or Path(os.environ["LOCALAPPDATA"]) /
              "MiniAndroidToolchain/android-sdk/platform-tools/adb.exe")
    if not Path(adb).is_file():
        adb = shutil.which("adb") or adb

    def outer(command):
        return subprocess.check_output([adb, "-s", args.serial, "shell", command],
                                       text=True, encoding="utf-8", timeout=30).strip()

    env = []
    for line in outer("/sbin/leo-framework-exec /system/bin/cat /init.environ.rc").splitlines():
        fields = line.split()
        if len(fields) == 3 and fields[0] == "export":
            env.append(fields[1] + "=" + fields[2])

    def android(command):
        return outer(shlex.join(["/sbin/leo-framework-exec", "/system/bin/env", *env,
                                 "/system/bin/sh", "-c", command]))

    if android("getprop ro.build.version.sdk") != "27":
        raise RuntimeError("Expected the Android API 27 profile")
    if args.screen != "keep":
        android("input keyevent " + ("224" if args.screen == "awake" else "223"))
        time.sleep(2)
    battery_before = android("dumpsys battery")
    power_before = android("dumpsys power | grep mWakefulness")
    pid = android("pidof ro.leo.visual").split()[0]
    hz = clock_ticks(base64.b64decode(android("base64 /proc/" + pid + "/auxv")))
    apk = android("pm path ro.leo.visual").removeprefix("package:")
    apk_sha = android("sha256sum " + shlex.quote(apk)).split()[0]
    samples = []
    start = time.monotonic()
    for index in range(args.samples):
        if index:
            time.sleep(max(0, start + index * args.interval - time.monotonic()))
        raw = android("cat /proc/uptime; cat /proc/" + pid + "/stat; "
                      "grep -E 'VmRSS:|Threads:' /proc/" + pid + "/status")
        lines = raw.splitlines()
        values = dict(re.findall(r"(VmRSS|Threads):\s+(\d+)", raw))
        samples.append({"pid": int(pid), "uptime_seconds": float(lines[0].split()[0]),
                        "cpu_ticks": cpu_ticks(lines[1]), "rss_kib": int(values["VmRSS"]),
                        "threads": int(values["Threads"])})
        print(f"{args.label}: sample {index + 1}/{args.samples}", flush=True)
    memory = android("dumpsys meminfo " + pid)
    power_after = android("dumpsys power | grep mWakefulness")
    result = {"format": "keyshell-resource-profile", "schema": 1,
              "recorded_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "label": args.label, "serial": args.serial, "apk_sha256": apk_sha,
              "clock_ticks_per_second": hz, "samples": samples,
              "cpu_percent_one_core": cpu_percent(samples[0], samples[-1], hz),
              "battery_before": battery_before, "battery_after": android("dumpsys battery"),
              "power_before": power_before, "power_after": power_after,
              "meminfo": memory,
              "limitation": "ADB attached; USB power and short intervals cannot establish battery life. "
                            "CPU includes diagnostic sampling overhead; RSS is not PSS."}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(args.output), "cpu_percent_one_core": result["cpu_percent_one_core"],
                      "rss_kib": samples[-1]["rss_kib"], "power_after": power_after}), flush=True)


if __name__ == "__main__":
    main()
