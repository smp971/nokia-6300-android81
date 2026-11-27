"""Synthetic model for isolated browser tests only. Not packaged in the Android app."""
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "visual-shell"


def model():
    catalog = (SOURCE / "AppCatalog.java").read_text(encoding="utf-8")
    folders = (SOURCE / "LauncherFolders.java").read_text(encoding="utf-8")
    appearance = (SOURCE / "Appearance.java").read_text(encoding="utf-8")
    hidden = set(map(int, re.findall(r"id==(\d+)", re.search(r"static boolean settingsOnly.*?\}", catalog).group())))
    groups = [list(map(int, re.findall(r"\d+", row))) for row in re.findall(r"\{([^{}]+)\}", re.search(r"DEFAULTS = \{(.*?)\n    \};", folders, re.S).group(1))]
    apps = [{"id": int(number), "name": name, "folder": next((i for i, group in enumerate(groups) if int(number) in group), -1)}
            for number, name in re.findall(r'new Entry\((\d+), "([^"]+)"', catalog) if int(number) not in hidden]
    names = re.findall(r'"([^"]+)"', re.search(r"THEMES=L.array\(new String\[\]\{(.*?)\}\)", appearance).group(1))
    colors = re.findall(r"\{0x([0-9a-f]+),0x[0-9a-f]+,0x([0-9a-f]+)\}", appearance)
    folder_names = re.findall(r'"([^"]+)"', re.search(r"NAMES = \{(.*?)\};", folders).group(1))
    assert len(apps) == len({app["id"] for app in apps}) and len(groups) == len(folder_names) == 5
    assert len(colors) == len(names)
    model = {"apps": apps, "folders": [{"id": i, "name": name} for i, name in enumerate(folder_names)],
             "themes": [{"id": i, "name": name, "color": "#" + colors[i][1][-6:], "background": "#" + colors[i][0][-6:]} for i, name in enumerate(names)],
             "settings": {"theme": 17, "list": False, "foldersEnabled": True, "simple": False},
             "status": {"platform": "KeyShell · Android 8.1", "api": 27, "battery": 86, "charging": False,
                        "health": "Exemplu demonstrativ", "free_bytes": 1459617792, "ram_free": 201326592, "ram_total": 536870912, "uptime_ms": 7200000}}
    return model
