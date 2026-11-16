"""Smoke-test the installed Leo UI on the unlocked, idle Nokia development phone."""

import argparse
import re
import subprocess
import sys
import time
from pathlib import Path
from xml.etree import ElementTree

FRAMEWORK = Path(__file__).with_name("framework-command.py")
SERIAL = "695d"
PACKAGE = "ro.leo.visual"
HOME = PACKAGE + "/.MainActivity"
SETTINGS = PACKAGE + "/.SettingsActivity"
NOTIFICATIONS = PACKAGE + "/.NotificationsActivity"
KEYPAD = "/dev/input/event1"
PREFS = "/data/user/0/ro.leo.visual/shared_prefs"
OK, BACK, MENU, SOFT_LEFT, LEFT, RIGHT = 352, 14, 212, 139, 105, 106
COMMANDS = (0, 1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 13, 12, 14, 15, 16)
SETTINGS_COMMAND = 4


class SmokeFailure(RuntimeError):
    pass


def shell(command):
    result = subprocess.run(
        [sys.executable, "-X", "utf8", str(FRAMEWORK), "--timeout", "6",
         "/system/bin/sh", "-c", command],
        capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=13,
    )
    if result.returncode:
        raise SmokeFailure("A bounded device command failed.")
    return result.stdout.strip()


def require(condition, message):
    if not condition:
        raise SmokeFailure(message)


def wait_for(probe, message):
    limit = time.monotonic() + 5
    while True:
        value = probe()
        if value:
            return value
        if time.monotonic() >= limit:
            raise SmokeFailure(message)
        time.sleep(.2)


def press(scan):
    shell(
        f"sendevent {KEYPAD} 1 {scan} 1; sendevent {KEYPAD} 0 0 0; "
        f"sleep .10; sendevent {KEYPAD} 1 {scan} 0; sendevent {KEYPAD} 0 0 0"
    )
    time.sleep(.3)


def focus():
    line = shell("dumpsys window windows | grep -F mCurrentFocus")
    return line.split("mCurrentFocus=", 1)[-1]


def resumed():
    line = shell("dumpsys activity activities | grep -F mResumedActivity || true")
    name = re.search(r"\bu\d+ ([^\s}]+/[^\s}]+)", line)
    return name.group(1) if name else ""


def pref(file, name, default=None):
    line = shell(f"""grep -F 'name="{name}"' {PREFS}/{file}.xml 2>/dev/null || true""")
    if not line:
        return default
    try:
        entry = ElementTree.fromstring(line)
        return (entry.text or "") if entry.tag == "string" else entry.get("value", default)
    except ElementTree.ParseError as error:
        raise SmokeFailure("A navigation preference could not be read.") from error


def show_home():
    shell("input keyevent 224; wm dismiss-keyguard; input keyevent 3; "
          "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME")
    wait_for(lambda: resumed() == HOME, "Leo is not the active Home.")
    return wait_for(lambda: (window := focus()) and PACKAGE + "/" in window and window,
                    "Unlock the Nokia first.")


def overlay_after(original):
    current = focus()
    return current if current != original and PACKAGE in current else ""


def menu_pins():
    pins = []
    for part in pref("home-menu", "global-menu-order", "").split(","):
        if re.fullmatch(r"[+-]?[0-9]+", part.strip()):
            command = int(part)
            if command in COMMANDS and command not in pins:
                pins.append(command)
                if len(pins) == 6:
                    break
    return pins or list(COMMANDS[:6])


def settings_list():
    lines = shell(r"dumpsys activity top | grep -E 'ACTIVITY |android.widget.ListView\{' || true")
    in_settings = False
    for line in lines.splitlines():
        if "ACTIVITY " in line:
            in_settings = SETTINGS in line
        elif in_settings:
            view = re.search(r"android\.widget\.ListView\{([0-9a-fA-F]+)", line)
            if view:
                return view.group(1)
    return ""


def preflight():
    boot = shell("cat /proc/cmdline")
    require(f"androidboot.serialno={SERIAL}" in boot.split(), "Unexpected phone serial.")
    require(shell("getprop ro.build.version.sdk") == "27", "The Nokia must be running Android API 27.")
    require(shell("cat /sys/class/input/event1/device/name") == "matrix_keypad", "Matrix keypad not found.")
    layout = shell("cat /system/usr/keylayout/matrix_keypad.kl")
    expected = {OK: "DPAD_CENTER", BACK: "BACK", MENU: "MENU", SOFT_LEFT: "MENU",
                LEFT: "DPAD_LEFT", RIGHT: "DPAD_RIGHT"}
    expected.update({index + 1: str(index) for index in range(1, 8)})
    actual = {int(scan): key for scan, key in re.findall(r"(?m)^\s*key\s+(\d+)\s+(\w+)", layout)}
    require(all(actual.get(scan) == key for scan, key in expected.items()), "Unexpected keypad layout.")
    states = re.findall(r"mCallState=(\d+)", shell("dumpsys telephony.registry | grep mCallState"))
    require(states and set(states) == {"0"}, "Leave calls idle for the keypad smoke test.")
    require(pref("keyguard", "locked") != "true", "Unlock Leo before the keypad smoke test.")


def smoke(home):
    press(OK)
    press(SOFT_LEFT)
    wait_for(lambda: overlay_after(home) and resumed() == HOME, "OK did not reach the app menu and Options.")
    print("PASS: Home OK opens apps, and the left softkey opens app options.", flush=True)
    press(BACK)
    wait_for(lambda: focus() == home, "Dedicated Back did not dismiss app options.")
    press(BACK)
    press(SOFT_LEFT)
    wait_for(lambda: "StatusBar" in focus() or resumed() == NOTIFICATIONS,
             "Dedicated Back did not return to the Home notification shortcut.")
    print("PASS: Dedicated Back returns to Home, where the left softkey opens notifications.", flush=True)
    press(BACK)
    wait_for(lambda: focus() == home and resumed() == HOME, "Notifications did not close.")

    pins = menu_pins()
    press(MENU)
    menu = wait_for(lambda: overlay_after(home), "Dedicated Menu did not open the universal overlay.")
    print("PASS: Dedicated Menu opens the universal Leo menu.", flush=True)
    if SETTINGS_COMMAND not in pins:
        press(len(pins) + 2)
        wait_for(lambda: overlay_after(home) and focus() != menu, "More did not open in the universal menu.")
        pins = [command for command in COMMANDS if command not in pins]
    press(pins.index(SETTINGS_COMMAND) + 2)
    wait_for(lambda: resumed() == SETTINGS, "Universal Menu did not open Leo Settings.")
    print("PASS: Universal Menu routes to Leo Settings.", flush=True)

    if pref("appearance", "simple-mode") == "true":
        print("SKIP: Everyday Settings has no category tabs.", flush=True)
    else:
        initial_category = pref("settings-ui", "category", "0")
        initial = wait_for(settings_list, "Could not identify the Settings list.")
        press(RIGHT)
        next_list = wait_for(lambda: (value := settings_list()) and value != initial and value,
                             "Right did not switch the Settings category.")
        press(LEFT)
        wait_for(lambda: (value := settings_list()) and value != next_list,
                 "Left did not switch back to the previous Settings category.")
        press(BACK)
        wait_for(lambda: resumed() == HOME, "Dedicated Back did not leave Settings.")
        require(pref("settings-ui", "category", "0") == initial_category,
                "Settings did not preserve its original category.")
        print("PASS: Right and Left switch Settings categories and preserve the original tab.", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, choices=[SERIAL])
    parser.parse_args()
    preflight()
    home = show_home()
    try:
        smoke(home)
    finally:
        try:
            foreground = resumed()
            if foreground == HOME and focus() != home:
                press(BACK)
            if foreground in {HOME, SETTINGS, NOTIFICATIONS}:
                show_home()
        except (SmokeFailure, OSError, subprocess.TimeoutExpired):
            print("Could not restore Home automatically.", file=sys.stderr)


if __name__ == "__main__":
    try:
        main()
    except (SmokeFailure, OSError, subprocess.TimeoutExpired) as error:
        print(f"Keypad smoke failed: {error}", file=sys.stderr)
        sys.exit(1)
