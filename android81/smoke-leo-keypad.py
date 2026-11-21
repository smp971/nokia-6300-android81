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
HOME_ACTIVITIES = {HOME, PACKAGE + "/.InternalHome"}
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
        raise SmokeFailure("A bounded device command failed: " + command +
                           ("; " + result.stderr.strip()[:240] if result.stderr.strip() else ""))
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


def parse_focused_window(raw):
    found = re.search(r"mCurrentFocus=(Window\{[^\n]+\})", raw)
    if not found:
        return "", None
    current = found.group(1)
    for entry in re.finditer(r"(?m)^\s*Window #\d+ (Window\{[^\n]+\}):", raw):
        if entry.group(1) == current:
            block = re.split(r"(?m)^\s*Window #", raw[entry.end():], maxsplit=1)[0]
            kind = re.search(r"\bty=(\d+)\b", block)
            return current, int(kind.group(1)) if kind else None
    return current, None


def focused_window():
    return parse_focused_window(shell("dumpsys window windows"))


def home_focused():
    current, kind = focused_window()
    return resumed() in HOME_ACTIVITIES and PACKAGE in current and kind == 1


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
    wait_for(lambda: resumed() in HOME_ACTIVITIES, "Leo is not the active Home.")
    for _ in range(3):
        current, kind = focused_window()
        if kind == 1 or PACKAGE not in current:
            break
        press(BACK)
    wait_for(home_focused, "Unlock the Nokia first.")
    return focus()


def overlay_after(original):
    current, kind = focused_window()
    # An Activity Dialog (2) may have exactly the same title as its owning Home (1).
    return current if current != original and PACKAGE in current and kind in {2, 2032} else ""


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
    enabled = shell("settings get secure enabled_accessibility_services").split(":")
    require(any(value in enabled for value in (PACKAGE + "/.LeoPointerService",
            PACKAGE + "/" + PACKAGE + ".LeoPointerService")),
            "Restore the existing keypad accessibility grant before this test; force-stop removes it.")
    require(shell("settings get secure accessibility_enabled") == "1",
            "Accessibility must be enabled for the dedicated global Menu key.")


def smoke(home):
    press(OK)
    press(SOFT_LEFT)
    wait_for(lambda: overlay_after(home) and resumed() in HOME_ACTIVITIES, "OK did not reach the app menu and Options.")
    print("PASS: Home OK opens apps, and the left softkey opens app options.", flush=True)
    press(BACK)
    wait_for(home_focused, "Dedicated Back did not dismiss app options.")
    press(BACK)
    press(SOFT_LEFT)
    wait_for(lambda: "StatusBar" in focus() or resumed() == NOTIFICATIONS,
             "Dedicated Back did not return to the Home notification shortcut.")
    print("PASS: Dedicated Back returns to Home, where the left softkey opens notifications.", flush=True)
    press(BACK)
    wait_for(home_focused, "Notifications did not close.")

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
        wait_for(lambda: resumed() in HOME_ACTIVITIES, "Dedicated Back did not leave Settings.")
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
            if foreground in HOME_ACTIVITIES and not home_focused():
                press(BACK)
            if foreground in HOME_ACTIVITIES | {SETTINGS, NOTIFICATIONS}:
                show_home()
        except (SmokeFailure, OSError, subprocess.TimeoutExpired):
            print("Could not restore Home automatically.", file=sys.stderr)


if __name__ == "__main__":
    try:
        main()
    except (SmokeFailure, OSError, subprocess.TimeoutExpired) as error:
        print(f"Keypad smoke failed: {error}", file=sys.stderr)
        sys.exit(1)
