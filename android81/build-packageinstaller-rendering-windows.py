"""Create a scoped PackageInstaller rendering update from a verified device backup.

No device access, downloads, new permissions, DEX or resource-table changes. Requires
the matching existing AOSP development signer and portable Windows SDK/JDK.
"""
from pathlib import Path
import argparse
import base64
import hashlib
import json
import re
import subprocess
import tempfile
import zipfile
from packageinstaller_rendering import binary_manifest, binary_title_style, HEADER_LAYOUTS

ROOT = Path(__file__).resolve().parent

def build(source, expected, output, tools):
    source, output, tools = source.resolve(), output.resolve(), tools.resolve()
    if output.exists() or source == output:
        raise ValueError("Choose a new output APK; the baseline is never overwritten")
    if hashlib.sha256(source.read_bytes()).hexdigest() != expected:
        raise ValueError("Baseline SHA-256 mismatch")
    java = next((tools / "jdk").glob("*/bin/java.exe"))
    bt = next((tools / "build-tools").iterdir())
    def run(*args):
        return subprocess.check_output([str(arg) for arg in args], text=True, encoding="utf-8", errors="strict")
    def cert(apk):
        text = run(java, "-jar", bt / "lib/apksigner.jar", "verify", "--print-certs", apk)
        digests = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)", text)
        if len(digests) != 1:
            raise ValueError("Expected one verified signer")
        return digests[0]
    badging = run(bt / "aapt.exe", "dump", "badging", source).splitlines()[0]
    if "name='com.android.packageinstaller'" not in badging:
        raise ValueError("This fallback only changes Android PackageInstaller")
    signer = cert(source)
    key = None
    for name in ("platform", "testkey"):
        pem = (tools / (name + ".x509.pem")).read_text()
        der = base64.b64decode("".join(line for line in pem.splitlines() if not line.startswith("---")))
        if hashlib.sha256(der).hexdigest() == signer:
            key = name
    if key is None:
        raise ValueError("Matching existing development signer is unavailable")
    output.parent.mkdir(parents=True, exist_ok=True)
    layouts = ["res/layout/" + name for name in HEADER_LAYOUTS]
    resources = run(bt / "aapt.exe", "dump", "resources", source)
    match = re.search(r"spec resource (0x[a-f0-9]+) com\.android\.packageinstaller:id/app_name:", resources)
    if not match:
        raise ValueError("App-name resource ID unavailable")
    app_name_id = int(match.group(1), 16)
    with tempfile.TemporaryDirectory(prefix="installer-canvas-", dir=output.parent) as directory:
        work = Path(directory).resolve()
        if not work.is_relative_to(output.parent):
            raise ValueError("Temporary build escaped output directory")
        unsigned, aligned, signed = (work / name for name in ("unsigned.apk", "aligned.apk", "signed.apk"))
        with zipfile.ZipFile(source) as old, zipfile.ZipFile(unsigned, "w") as new:
            if len(set(old.namelist())) != len(old.namelist()):
                raise ValueError("Duplicate baseline ZIP entries")
            manifest = binary_manifest(old.read("AndroidManifest.xml"))
            for entry in old.infolist():
                if re.fullmatch(r"META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC))", entry.filename, re.I):
                    continue
                data = old.read(entry)
                if entry.filename == "AndroidManifest.xml":
                    data = manifest
                elif entry.filename in layouts:
                    data = binary_title_style(data, app_name_id)
                new.writestr(entry, data)
        run(bt / "zipalign.exe", "-f", "4", unsigned, aligned)
        run(java, "-jar", bt / "lib/apksigner.jar", "sign", "--key", tools / (key + ".pk8"),
            "--cert", tools / (key + ".x509.pem"), "--out", signed, aligned)
        if cert(signed) != signer or run(bt / "aapt.exe", "dump", "badging", signed).splitlines()[0] != badging:
            raise ValueError("Package identity/version/signer changed")
        before = run(bt / "aapt.exe", "dump", "xmltree", source, "AndroidManifest.xml").splitlines()
        after = run(bt / "aapt.exe", "dump", "xmltree", signed, "AndroidManifest.xml").splitlines()
        rendering = "A: android:hardwareAccelerated(0x010102d3)=(type 0x12)0x0"
        added = [line for line in after if rendering in line]
        if len(added) != 1 or [line for line in after if rendering not in line] != before:
            raise ValueError("aapt reports a manifest change beyond the new false rendering attribute")
        for layout in layouts:
            before_layout = run(bt / "aapt.exe", "dump", "xmltree", source, layout)
            after_layout = run(bt / "aapt.exe", "dump", "xmltree", signed, layout)
            expected_layout = before_layout.replace("android:textAppearance(0x01010034)=?0x10102f8", "android:textAppearance(0x01010034)=?0x1010040")
            if after_layout != expected_layout or after_layout == before_layout:
                raise ValueError("Unexpected header layout delta: " + layout)
        with zipfile.ZipFile(source) as old, zipfile.ZipFile(signed) as new:
            preserved = [name for name in old.namelist() if not name.startswith("META-INF/") and name not in ["AndroidManifest.xml", *layouts]]
            if any(old.read(name) != new.read(name) for name in preserved):
                raise ValueError("DEX or resource payload changed")
        output.write_bytes(signed.read_bytes())
    report = {"package": "com.android.packageinstaller", "before": expected,
        "after": hashlib.sha256(output.read_bytes()).hexdigest(), "signer": signer,
        "change": "application hardwareAccelerated=false; six app-title styles use textAppearanceLarge", "preserved_payload_entries": len(preserved),
        "identity_version_unchanged": True, "dex_resource_table_unchanged": True, "header_layouts": layouts,
        "aapt_manifest_layout_deltas_verified": True}
    output.with_suffix(".json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--expected-sha256", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--tools", type=Path, default=ROOT / "output/windows-build-tools")
    args = parser.parse_args()
    build(args.baseline, args.expected_sha256, args.output, args.tools)
