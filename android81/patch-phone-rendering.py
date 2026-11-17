"""Use an alpha-capable software canvas for TeleService's Mobile network activity."""

import argparse
import re
from pathlib import Path


TELEPHONY = Path("/root/android81-leo/src/packages/services/Telephony")
ACTIVITY_SOURCE = Path("src/com/android/phone/MobileNetworkSettings.java")
MOBILE_NETWORK = re.compile(
    r'<activity\b[^>]*\bandroid:name="(?:com\.android\.phone\.|\.)?MobileNetworkSettings"[^>]*>'
)
ACCELERATION = re.compile(r'\bandroid:hardwareAccelerated="[^"]*"')


def patch_manifest(manifest):
    def software(match):
        declaration = match.group()
        if ACCELERATION.search(declaration):
            return ACCELERATION.sub('android:hardwareAccelerated="false"', declaration)
        return declaration.replace("<activity", '<activity android:hardwareAccelerated="false"', 1)

    patched, matches = MOBILE_NETWORK.subn(software, manifest)
    if matches != 1:
        raise ValueError("Expected exactly one MobileNetworkSettings activity")
    return patched


def patch_activity(source):
    anchor = "        super.onCreate(savedInstanceState);\n        setContentView(R.layout.network_setting);"
    patched = """        super.onCreate(savedInstanceState);
        // ViewRootImpl clears software buffers with alpha before painting.
        // An opaque RGBX buffer can retain pixels from the previous SIM layout.
        getWindow().setFormat(android.graphics.PixelFormat.TRANSLUCENT);
        setContentView(R.layout.network_setting);"""
    if source.count(patched) == 1:
        return source
    if source.count(anchor) != 1:
        raise ValueError("Expected MobileNetworkSettings.onCreate to set the network layout")
    return source.replace(anchor, patched, 1)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("telephony", nargs="?", type=Path, default=TELEPHONY)
    args = parser.parse_args()
    manifest_path = args.telephony / "AndroidManifest.xml"
    activity_path = args.telephony / ACTIVITY_SOURCE
    manifest = manifest_path.read_text(encoding="utf-8")
    activity = activity_path.read_text(encoding="utf-8")
    patched_manifest = patch_manifest(manifest)
    patched_activity = patch_activity(activity)
    if patched_manifest != manifest:
        manifest_path.write_text(patched_manifest, encoding="utf-8")
    if patched_activity != activity:
        activity_path.write_text(patched_activity, encoding="utf-8")
