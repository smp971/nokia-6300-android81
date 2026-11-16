"""Rebuild the installed Leo Settings APK with optional OEM lock lookups."""

import argparse
import hashlib
import re
import subprocess
import tempfile
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parent
TOOLS = ROOT / "output/windows-build-tools"
BASELINE_SHA256 = "ed5bc737ed0d2d744219f59587d87e14cade279d1407dc0d37d4b1eb1ccd7cb8"


def run(*command):
    return subprocess.check_output([str(arg) for arg in command], text=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    baseline, output = args.baseline.resolve(), args.output.resolve()
    if hashlib.sha256(baseline.read_bytes()).hexdigest() != BASELINE_SHA256:
        parser.error("expected the verified Leo Settings keypad APK")
    if baseline == output:
        parser.error("choose a different output path")

    java = next(TOOLS.glob("jdk/*/bin/java.exe"))
    build_tools = next(TOOLS.glob("build-tools/*/zipalign.exe")).parent
    signer = (java, "-jar", build_tools / "lib/apksigner.jar")
    pattern = r"Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)"
    baseline_cert = re.findall(pattern, run(*signer, "verify", "--print-certs", baseline))
    jars = [TOOLS / name for name in ("dexlib2.jar", "guava.jar", "jsr305.jar")]
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=output.parent) as directory:
        work = Path(directory)
        cp = ";".join(map(str, [work, *jars]))
        run(java.with_name("javac.exe"), "-cp", cp, "-d", work,
            ROOT / "framework-keypad/PatchSettingsOemLock.java")
        unsigned, aligned, signed = (work / name for name in ("unsigned.apk", "aligned.apk", "signed.apk"))
        with ZipFile(baseline) as original, ZipFile(unsigned, "w") as candidate:
            dex = Path(original.extract("classes.dex", work))
            changed = work / "classes-patched.dex"
            run(java, "-Xmx1024m", "-cp", cp, "PatchSettingsOemLock", dex, changed)
            for entry in original.infolist():
                if not entry.filename.startswith("META-INF/"):
                    data = changed.read_bytes() if entry.filename == "classes.dex" else original.read(entry)
                    candidate.writestr(entry, data)
        run(build_tools / "zipalign.exe", "-f", "4", unsigned, aligned)
        run(*signer, "sign", "--key", TOOLS / "platform.pk8", "--cert", TOOLS / "platform.x509.pem",
            "--out", signed, aligned)
        if len(baseline_cert) != 1 or re.findall(pattern, run(*signer, "verify", "--print-certs", signed)) != baseline_cert:
            raise RuntimeError("Settings signing certificate changed")
        with ZipFile(baseline) as original, ZipFile(signed) as candidate:
            entries = [name for name in original.namelist()
                       if name != "classes.dex" and not name.startswith("META-INF/")]
            if any(original.read(name) != candidate.read(name) for name in entries):
                raise RuntimeError("Settings resources changed")
        signed.replace(output)
    print(f"{hashlib.sha256(output.read_bytes()).hexdigest()}  {output}")


if __name__ == "__main__":
    main()
