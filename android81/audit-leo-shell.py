"""Inventory Leo Visual entry points from source without launching the app."""

import argparse
import json
import re
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path


SOURCE = Path(__file__).resolve().parent / "visual-shell"
ANDROID = "{http://schemas.android.com/apk/res/android}"
COMPONENTS = {"activity", "activity-alias", "provider", "receiver", "service"}


def inventory():
    app = ET.parse(SOURCE / "AndroidManifest.xml").getroot().find("application")
    components = []
    for node in app:
        if node.tag not in COMPONENTS:
            continue
        name = node.attrib[ANDROID + "name"].rsplit(".", 1)[-1]
        target = node.get(ANDROID + "targetActivity", name).rsplit(".", 1)[-1]
        if not (SOURCE / (target + ".java")).is_file():
            raise ValueError(f"No Java source for {name}: {target}")
        components.append({
            "kind": node.tag,
            "name": name,
            "exported": node.get(ANDROID + "exported"),
            "permission": node.get(ANDROID + "permission"),
            "process": node.get(ANDROID + "process"),
            "actions": [action.attrib[ANDROID + "name"] for action in node.findall("intent-filter/action")],
        })
    catalog = (SOURCE / "AppCatalog.java").read_text(encoding="utf-8")
    names = re.search(r"String\[\] NAMES\s*=\s*L\.array\(new String\[\]\{([^}]+)\}", catalog)
    hidden = re.search(r"boolean settingsOnly\(int id\)\s*\{return ([^;]+);", catalog)
    if names is None or hidden is None:
        raise ValueError("AppCatalog format changed; update the audit parser")
    labels = re.findall(r'"([^"]+)"', names[1])
    hidden_ids = {int(i) for i in re.findall(r"id\s*==\s*(\d+)", hidden[1])}
    if not hidden_ids <= set(range(len(labels))):
        raise ValueError("Settings-only ID outside the catalog")
    # Match current dispatch forms explicitly. If the launcher moves to a
    # registry, read that registry instead of guessing at arbitrary Java code.
    home = (SOURCE / "MainActivity.java").read_text(encoding="utf-8")
    dispatch = home.split("void open(int index) {", 1)[1].split("Intent intent;", 1)[0]
    routes = {}
    for condition, body in re.findall(r"if\s*\(([^)]+)\)\s*\{([^\n]+)", dispatch):
        targets = re.findall(r"(\w+Activity)\.class", body)
        if not targets:
            continue
        span = re.fullmatch(r"index\s*>=\s*(\d+)\s*&&\s*index\s*<=\s*(\d+)", condition)
        ids = list(range(int(span[1]), int(span[2]) + 1)) if span else [
            int(match) for match in re.findall(r"index\s*==\s*(\d+)", condition)
        ]
        if not ids or len(targets) not in (1, len(ids)):
            raise ValueError(f"Unsupported launch condition: {condition}")
        for app_id, target in zip(ids, targets * len(ids) if len(targets) == 1 else targets):
            if app_id in routes:
                raise ValueError(f"Duplicate route for app {app_id}")
            routes[app_id] = target
    if routes.keys() != set(range(len(labels))):
        raise ValueError(f"Catalog and route IDs differ: {sorted(routes.keys() ^ set(range(len(labels))))}")
    activities = {item["name"] for item in components if item["kind"] == "activity"}
    if missing := set(routes.values()) - activities:
        raise ValueError(f"Routes missing from manifest: {sorted(missing)}")
    return {
        "catalog": [
            {"id": i, "label_ro": label, "activity": routes[i], "hidden_from_grid": i in hidden_ids}
            for i, label in enumerate(labels)
        ],
        "components": components,
        "source_files": len(list(SOURCE.glob("*.java"))),
        "test_sources": len(list((SOURCE / "tests").glob("*.java"))),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json", type=Path, metavar="OUTPUT", help="Save the complete inventory as UTF-8 JSON")
    args = parser.parse_args()
    data = inventory()
    kinds = Counter(item["kind"] for item in data["components"])
    hidden = sum(row["hidden_from_grid"] for row in data["catalog"])
    print(f"{len(data['catalog'])} catalog IDs with native routes ({hidden} hidden from the grid)")
    print(", ".join(f"{key}={count}" for key, count in sorted(kinds.items())))
    print(f"{data['source_files']} Java source files; {data['test_sources']} Java test sources")
    if args.json:
        args.json.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
