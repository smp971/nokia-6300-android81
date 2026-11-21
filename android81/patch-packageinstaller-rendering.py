"""Disable PackageInstaller HWUI in the AOSP source manifest for the Leo renderer."""
from pathlib import Path
import argparse
from packageinstaller_rendering import source_manifest, source_layout, HEADER_LAYOUTS

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=Path("/root/android81-leo/src"))
    args = parser.parse_args()
    path = args.source / "packages/apps/PackageInstaller/AndroidManifest.xml"
    changes = [(path, source_manifest(path.read_text(encoding="utf-8")))]
    for name in HEADER_LAYOUTS:
        layout = path.parent / "res/layout" / name
        changes.append((layout, source_layout(layout.read_text(encoding="utf-8"))))
    for destination, after in changes:
        if after != destination.read_text(encoding="utf-8"):
            destination.write_text(after, encoding="utf-8")
    print("PackageInstaller uses software Canvas and theme-aware large app titles")

if __name__ == "__main__":
    main()
