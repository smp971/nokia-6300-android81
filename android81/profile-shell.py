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


MEDIA_PROCESSES={"audioserver","media.codec","media.extractor","mediaserver"}


def process_rows(text,include_media=False):
    rows=[]
    for line in text.splitlines():
        fields=line.split(None,1)
        if len(fields)!=2 or not fields[0].isdigit():continue
        name=fields[1].strip()
        if name in ("ro.leo.visual","ro.leo.keypad","com.android.webview") or name.startswith(("ro.leo.visual:","ro.leo.keypad:","com.android.webview:")) or include_media and name in MEDIA_PROCESSES:
            rows.append((int(fields[0]),name))
    return rows


def pss_kib(text):
    value=re.search(r"^\s*TOTAL\s+(\d+)\b",text,re.MULTILINE)
    return int(value.group(1)) if value else None


def system_memory(text):
    return {key:int(value) for key,value in re.findall(r"^(MemTotal|MemAvailable|MemFree|Cached|SwapTotal|SwapFree):\s+(\d+) kB",text,re.MULTILINE)}


def all_processes(android,args,hz,apk_sha,battery_before,power_before):
    windows={};started=time.monotonic();sets=[]
    for index in range(args.samples):
        if index:time.sleep(max(0,started+index*args.interval-time.monotonic()))
        found=process_rows(android("ps -A -o PID,NAME"),args.include_media);seen=set()
        for pid,name in found:
            try:
                raw=android(f"cat /proc/uptime; cat /proc/{pid}/stat; grep -E 'VmRSS:|Threads:' /proc/{pid}/status")
                lines=raw.splitlines();values=dict(re.findall(r"(VmRSS|Threads):\s+(\d+)",raw))
                stat=lines[1];birth=int(stat.rsplit(") ",1)[1].split()[19]);key=(pid,birth,name)
                sample={"pid":pid,"uptime_seconds":float(lines[0].split()[0]),"cpu_ticks":cpu_ticks(stat),"rss_kib":int(values["VmRSS"]),"threads":int(values["Threads"])}
                windows.setdefault(key,[]).append(sample);seen.add(key)
            except (ValueError,IndexError,KeyError,subprocess.CalledProcessError):
                continue
        sets.append(seen);print(f"{args.label}: sample {index+1}/{args.samples}, {len(seen)} processes",flush=True)
    result=[]
    for (pid,birth,name),samples in windows.items():
        memory="";stable=(pid,birth,name) in sets[-1]
        if stable:
            try:memory=android(f"dumpsys meminfo {pid}")
            except subprocess.CalledProcessError:pass
        result.append({"pid":pid,"start_ticks":birth,"name":name,"scope":"shared-media" if name in MEDIA_PROCESSES else "app","samples":samples,
                       "cpu_percent_one_core":cpu_percent(samples[0],samples[-1],hz) if len(samples)>1 else None,
                       "pss_kib":pss_kib(memory),"meminfo":memory,"present_at_end":stable})
    final=[r for r in result if r["present_at_end"]]
    apps=[r for r in final if r["scope"]=="app"];media=[r for r in final if r["scope"]=="shared-media"]
    def group_pss(group):return sum(r["pss_kib"] for r in group) if group and all(r["pss_kib"] is not None for r in group) else None
    complete=sets[0]==sets[-1] and all(r["cpu_percent_one_core"] is not None for r in final)
    return {"format":"keyshell-resource-profile","schema":2,"recorded_utc":datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "label":args.label,"serial":args.serial,"apk_sha256":apk_sha,"clock_ticks_per_second":hz,"processes":result,
            "total_cpu_percent_one_core":round(sum(r["cpu_percent_one_core"] for r in final),3) if complete else None,
            "total_pss_kib":sum(r["pss_kib"] for r in final) if final and all(r["pss_kib"] is not None for r in final) else None,
            "app_pss_kib":group_pss(apps),"shared_media_pss_kib":group_pss(media),
            "stable_process_set":complete,"system_memory_kib":system_memory(android("cat /proc/meminfo")),"battery_before":battery_before,"battery_after":android("dumpsys battery"),
            "power_before":power_before,"power_after":android("dumpsys power | grep mWakefulness"),
            "limitation":"USB-attached snapshot, not battery autonomy. PSS covers listed app/IME/WebView processes, not the whole OS. CPU includes sampling overhead; changing process sets have no aggregate CPU estimate."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, choices=["695d"])
    parser.add_argument("--adb", type=Path)
    parser.add_argument("--label", required=True)
    parser.add_argument("--all-processes",action="store_true",help="include shell child processes, keypad IME and the AOSP WebView package")
    parser.add_argument("--include-media",action="store_true",help="also report shared Android audio/codec services separately")
    parser.add_argument("--samples", type=int, default=4)
    parser.add_argument("--interval", type=float, default=15)
    parser.add_argument("--screen", choices=["keep", "awake", "asleep"], default="keep")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if args.include_media and not args.all_processes:parser.error("--include-media requires --all-processes")
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
    if args.all_processes:
        result=all_processes(android,args,hz,apk_sha,battery_before,power_before)
        args.output.parent.mkdir(parents=True,exist_ok=True)
        args.output.write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
        print(json.dumps({"output":str(args.output),"processes":len(result["processes"]),"total_pss_kib":result["total_pss_kib"],"total_cpu_percent_one_core":result["total_cpu_percent_one_core"]}),flush=True)
        return
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
