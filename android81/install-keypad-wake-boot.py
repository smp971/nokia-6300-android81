"""Check/install/restore the DT-only wake image on Nokia 695d. Never reboots."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import struct
import subprocess
import sys

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("wake_patch", HERE / "patch-keypad-wake-boot.py")
patch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(patch)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def tables(data):
    result, pos = [], 0
    while pos < len(data):
        patch.require(pos + 40 <= len(data), "Truncated table")
        length = struct.unpack_from(">I", data, pos + 4)[0]
        patch.require(40 <= length <= len(data) - pos, "Table bounds")
        result.append(data[pos:pos + length])
        pos += length
    return result


def validate(original, candidate):
    old_code, old_tables, old_ramdisk, page = patch.boot_parts(original)
    new_code, new_tables, new_ramdisk, new_page = patch.boot_parts(candidate)
    patch.require(old_code == new_code and old_ramdisk == new_ramdisk and page == new_page,
                  "Kernel code/ramdisk/layout differs")
    patch.require(original[12:576] == candidate[12:576]
                  and original[596:page] == candidate[596:page], "Boot header differs")
    before, after = tables(old_tables), tables(new_tables)
    patch.require(len(before) == len(after), "DTB count differs")
    changes = 0
    for old, new in zip(before, after):
        a, b = patch.fdt_snapshot(old), patch.fdt_snapshot(new)
        expected = dict(a[1])
        if patch.TARGET in a[0]:
            expected.setdefault((patch.TARGET, "wakeup-source"), b"")
        patch.require(a[0] == b[0] and expected == b[1] and a[2:] == b[2:],
                      "Unexpected DT change")
        changes += old != new
    patch.require(changes > 0, "No wake fix present")
    return changes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, choices=["695d"])
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--backup", type=Path, required=True)
    parser.add_argument("--recovery-backup", type=Path, required=True)
    parser.add_argument("--action", choices=["check", "install", "restore"], default="check")
    args = parser.parse_args()
    original, candidate, recovery = (p.read_bytes() for p in
                                    (args.backup, args.candidate, args.recovery_backup))
    patch.require(len(recovery) == 33554432, "Recovery backup must be 32 MiB")
    changed = validate(original, candidate)
    adb = ["D:/ROMS/platform-tools/adb.exe", "-s", args.serial]
    def native(*command):
        result = subprocess.run([sys.executable, "-X", "utf8", HERE / "framework-command.py",
                                 "--timeout", "30", *command], check=True, capture_output=True,
                                text=True, encoding="utf-8", timeout=38)
        return result.stdout.strip()
    def remote_sha(path):
        return native("/system/bin/sha256sum", path).split()[0]
    def idle():
        calls = re.findall(r"mCallState=(\d+)", native("/system/bin/sh", "-c",
            "dumpsys telephony.registry | grep mCallState"))
        patch.require(len(calls) == 2 and set(calls) == {"0"}, "Call active/state unavailable")
    patch.require("androidboot.serialno=695d" in native("/system/bin/cat", "/proc/cmdline"), "Wrong phone")
    patch.require("uid=0" in native("/system/bin/id"), "Root required")
    patch.require(native("/system/bin/getprop", "ro.build.version.sdk") == "27", "Wrong Android version")
    for number, name, device in [(29, "boot", "b3:1d"), (33, "recovery", "103:1")]:
        props = dict(row.split("=", 1) for row in native("/system/bin/cat",
            f"/sys/class/block/mmcblk0p{number}/uevent").splitlines() if "=" in row)
        patch.require(props.get("PARTNAME") == name, "Partition name mismatch")
        patch.require(native("/system/bin/cat", f"/sys/class/block/mmcblk0p{number}/size") == "65536",
                      "Partition size mismatch")
        patch.require(native("/system/bin/stat", "-L", "-c", "%t:%T",
            "/dev/block/bootdevice/by-name/" + name) == device, "Partition device mismatch")
    idle()
    battery = native("/system/bin/dumpsys", "battery")
    patch.require("USB powered: true" in battery, "Keep USB connected")
    level = re.search(r"\blevel:\s*(\d+)", battery)
    patch.require(level and int(level[1]) >= 30, "Battery below 30% or unavailable")
    boot = "/dev/block/bootdevice/by-name/boot"
    recover = "/dev/block/bootdevice/by-name/recovery"
    expected = sha(candidate) if args.action == "restore" else sha(original)
    patch.require(remote_sha(boot) == expected, "Live boot differs from the reviewed image")
    patch.require(remote_sha(recover) == sha(recovery), "Recovery differs from backup")
    result = {"action": args.action, "previous_boot_sha256": sha(original),
              "candidate_boot_sha256": sha(candidate), "recovery_sha256": sha(recovery),
              "changed_dtbs": changed, "kernel_code_and_ramdisk_unchanged": True,
              "flashed": False, "rebooted": False, "userdata_formatted": False}
    if args.action != "check":
        local = args.backup if args.action == "restore" else args.candidate
        target = sha(original) if args.action == "restore" else sha(candidate)
        staged = "/data/local/tmp/keyshell-reviewed-wake-boot.img"
        subprocess.run(adb + ["push", str(local), "/proc/1/root" + staged], check=True, timeout=60)
        patch.require(remote_sha(staged) == target, "Staged boot hash mismatch")
        idle()
        patch.require(remote_sha(boot) == expected, "Boot changed while staging")
        # The only partition write; recovery, system and userdata are untouched.
        native("/system/bin/dd", "if=" + staged, "of=" + boot, "bs=4096")
        native("/system/bin/sync")
        patch.require(remote_sha(boot) == target, "Boot readback mismatch")
        patch.require(remote_sha(recover) == sha(recovery), "Recovery changed")
        result.update(flashed=True, installed_boot_sha256=target)
    report = args.candidate.parent / ("wake-boot-" + args.action + ".json")
    report.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
