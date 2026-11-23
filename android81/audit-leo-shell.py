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


def catalog_entries(catalog):
    """Read the explicit registry; reject unknown syntax instead of inventing routes."""
    block = re.search(r"Entry\[\]\s+APPS\s*=\s*entries\(new Entry\[\]\s*\{(.*?)\}\);", catalog, re.S)
    hidden = re.search(r"boolean\s+settingsOnly\(int id\)\s*\{\s*return\s+([^;]+);", catalog)
    if block is None or hidden is None:
        raise ValueError("AppCatalog format changed; update the audit parser")
    quoted = r'"(?:[^"\\]|\\.)*"'
    entry = re.compile(r'new Entry\(\s*(\d+)\s*,\s*(' + quoted +
                       r')\s*,\s*(\w+Activity)\.class(?:\s*,\s*(' + quoted +
                       r')\s*,\s*(' + quoted + r'|true|false))?\s*\)\s*,?')
    rows = {}
    for line in block[1].splitlines():
        if not line.strip():
            continue
        match = entry.fullmatch(line.strip())
        if match is None:
            raise ValueError("Unsupported catalog entry: " + line.strip())
        app_id = int(match[1])
        if app_id in rows:
            raise ValueError("Duplicate catalog ID: " + str(app_id))
        row = {"id": app_id, "label_ro": json.loads(match[2]), "activity": match[3]}
        if match[4] is not None:
            row["extra"] = {"key": json.loads(match[4]), "value": json.loads(match[5])}
        rows[app_id] = row
    if not rows or set(rows) != set(range(len(rows))):
        raise ValueError("Catalog IDs must be contiguous from zero")
    hidden_ids = set()
    for condition in hidden[1].split("||"):
        match = re.fullmatch(r"\s*id\s*==\s*(\d+)\s*", condition)
        if match is None:
            raise ValueError("Unsupported settings-only condition")
        hidden_ids.add(int(match[1]))
    if not hidden_ids <= rows.keys():
        raise ValueError("Settings-only ID outside the catalog")
    return [dict(rows[i], hidden_from_grid=i in hidden_ids) for i in range(len(rows))]


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
    rows = catalog_entries(catalog)
    # Main must delegate to the same registry whose IDs were inventoried.
    home = (SOURCE / "MainActivity.java").read_text(encoding="utf-8")
    if not re.search(r"void open\(int index\)\s*\{\s*try\s*\{\s*if\(!AppCatalog\.launch\(MainActivity\.this,index\)\)", home):
        raise ValueError("Home no longer delegates to the inventoried registry")
    activities = {item["name"] for item in components if item["kind"] == "activity"}
    if missing := {row["activity"] for row in rows} - activities:
        raise ValueError(f"Routes missing from manifest: {sorted(missing)}")
    return {
        "catalog": rows,
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
