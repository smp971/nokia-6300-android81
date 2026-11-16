"""Build the API 27 resource overlays for Android's native dark surfaces."""

import argparse
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent
TOOLS = ROOT / "output/windows-build-tools"


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


def tool(pattern):
    matches = list(TOOLS.glob(pattern))
    if len(matches) != 1:
        raise SystemExit(f"Expected exactly one {pattern} under {TOOLS}")
    return matches[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--framework-res", required=True, type=Path,
                        help="framework-res.apk from the target Oreo build")
    parser.add_argument("--output", type=Path, default=ROOT / "output/android-dark")
    args = parser.parse_args()
    if not args.framework_res.is_file():
        parser.error("framework-res.apk is missing")
    java = tool("jdk/*/bin/java.exe")
    build_tools = tool("build-tools/*/aapt.exe").parent
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    for name in ("framework", "installer", "settings", "systemui"):
        source = ROOT / "system-dark" / name
        unsigned = output / f"{name}-unsigned.apk"
        aligned = output / f"{name}-aligned.apk"
        signed = output / f"leo-dark-{name}.apk"
        run(build_tools / "aapt.exe", "package", "-f", "-M", source / "AndroidManifest.xml",
            "-S", source / "res", "-I", args.framework_res.resolve(), "-F", unsigned)
        run(build_tools / "zipalign.exe", "-f", "4", unsigned, aligned)
        signer = build_tools / "lib/apksigner.jar"
        run(java, "-jar", signer, "sign", "--key", TOOLS / "platform.pk8",
            "--cert", TOOLS / "platform.x509.pem", "--out", signed, aligned)
        run(java, "-jar", signer, "verify", signed)
        print(signed, flush=True)


if __name__ == "__main__":
    main()
