"""Build a signed Leo Visual APK from current sources with the Windows SDK."""

import argparse
import hashlib
import os
import subprocess
import sys
import tempfile
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo


ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT / "output"
TOOLS = OUTPUT / "windows-build-tools"
BUILD = OUTPUT / "pocket-apps-build"
VCARD_REVISION = "d90a30beaf9c1e3b7a9633bb32a49f26fdb3b1e2"


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


def only_tool(pattern):
    matches = sorted(TOOLS.glob(pattern))
    if len(matches) != 1:
        raise SystemExit(f"Expected exactly one {pattern} under {TOOLS}")
    return matches[0]


def zip_entry(archive, name, data):
    entry = ZipInfo(name)
    entry.compress_type = ZIP_DEFLATED
    archive.writestr(entry, data)


def source_args(path, sources):
    path.write_text("".join(f'"{source.as_posix()}"\n' for source in sorted(sources)), encoding="utf-8")
    return "@" + str(path)


def vcard_library(javac, sdk, work, supplied, source):
    if supplied is not None:
        with ZipFile(supplied) as dependency:
            if "com/android/vcard/VCardParser.class" not in dependency.namelist():
                raise SystemExit("The supplied vCard JAR does not contain the AOSP vCard classes.")
        return supplied
    if not (source / "java").is_dir():
        raise SystemExit(f"Missing pinned vCard checkout: {source}. See android81/REBUILD.md.")
    git = ["git", "-C", str(source)]
    revision = subprocess.check_output(git + ["rev-parse", "HEAD"], text=True).strip()
    dirty = subprocess.check_output(git + ["status", "--porcelain", "--", "java"], text=True).strip()
    if revision != VCARD_REVISION or dirty:
        raise SystemExit(f"Expected unmodified vCard Java sources at {VCARD_REVISION}.")
    classes = work / "vcard"
    classes.mkdir()
    run(javac, "-encoding", "UTF-8", "-source", "1.8", "-target", "1.8", "-bootclasspath", sdk,
        "-d", classes, source_args(work / "vcard-sources.txt", (source / "java").rglob("*.java")))
    jar = work / "vcard.jar"
    with ZipFile(jar, "w") as archive:
        for path in sorted(classes.rglob("*.class")):
            zip_entry(archive, path.relative_to(classes).as_posix(), path.read_bytes())
    return jar


def build(vcard, vcard_source):
    java = only_tool("jdk/*/bin/java.exe")
    javac = java.with_name("javac.exe")
    sdk = only_tool("platform/*/android.jar")
    build_tools = only_tool("build-tools/*/aapt.exe").parent
    BUILD.mkdir(parents=True, exist_ok=True)
    run(sys.executable, ROOT / "localization/generate.py")
    run(sys.executable, ROOT / "build-webui-demo.py")
    with tempfile.TemporaryDirectory(prefix="source-", dir=BUILD) as temporary:
        work = Path(temporary)
        classes = work / "classes"
        dex = work / "dex"
        classes.mkdir()
        dex.mkdir()
        vcard = vcard_library(javac, sdk, work, vcard, vcard_source)
        run(sys.executable, ROOT / "prepare-pocket-dependencies.py", "--output", work / "dependencies")
        ssh = work / "dependencies/jsch.jar"
        sources = sorted(
            list((ROOT / "visual-shell").glob("*.java"))
            + list((ROOT / "third-party/pocket").glob("*.java"))
            + [ROOT / "localization" / name for name in ("L.java", "Language.java", "EnglishCatalog.java")]
        )
        run(javac, "-encoding", "UTF-8", "-source", "1.8", "-target", "1.8",
            "-bootclasspath", sdk, "-classpath", os.pathsep.join(map(str, (vcard, ssh))),
            "-d", classes, source_args(work / "sources.txt", sources))
        jar = work / "leo-visual-classes.jar"
        with ZipFile(jar, "w") as archive, ZipFile(ssh) as dependency:
            for path in sorted(classes.rglob("*.class")):
                zip_entry(archive, path.relative_to(classes).as_posix(), path.read_bytes())
            for name in sorted(dependency.namelist()):
                if name.endswith(".class"):
                    zip_entry(archive, name, dependency.read(name))
        run(java, "-cp", build_tools / "lib/d8.jar", "com.android.tools.r8.D8", "--min-api", "27",
            "--lib", sdk, "--output", dex, jar, vcard)
        if sorted(p.name for p in dex.glob("classes*.dex")) != ["classes.dex"]:
            raise RuntimeError("Unexpected multidex output")
        unsigned, aligned, signed = (work / name for name in ("unsigned.apk", "aligned.apk", "leo-visual.apk"))
        run(build_tools / "aapt.exe", "package", "-f", "-M", ROOT / "visual-shell/AndroidManifest.xml",
            "-S", ROOT / "visual-shell/res", "-A", ROOT / "visual-shell/assets", "-I", sdk, "-F", unsigned)
        with ZipFile(unsigned, "a") as archive:
            zip_entry(archive, "classes.dex", (dex / "classes.dex").read_bytes())
        run(build_tools / "zipalign.exe", "-f", "4", unsigned, aligned)
        signer = build_tools / "lib/apksigner.jar"
        run(java, "-jar", signer, "sign", "--key", TOOLS / "testkey.pk8",
            "--cert", TOOLS / "testkey.x509.pem", "--out", signed, aligned)
        run(java, "-jar", signer, "verify", "--verbose", "--print-certs", signed)
        jar.replace(BUILD / jar.name)
        signed.replace(BUILD / signed.name)
    print(f"Compiled {len(sources)} Java sources")
    print("Candidate SHA256:", hashlib.sha256((BUILD / "leo-visual.apk").read_bytes()).hexdigest())


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--vcard", type=Path, help="use a prebuilt AOSP vCard JAR")
    parser.add_argument("--vcard-source", type=Path, default=OUTPUT / "vcard-source")
    args = parser.parse_args()
    build(args.vcard.resolve() if args.vcard else None, args.vcard_source.resolve())
