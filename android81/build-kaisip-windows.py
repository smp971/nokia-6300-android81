"""Build the isolated Android KaiSIP port from pinned upstream assets."""
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parent
SOURCE=ROOT/"kaisip-android"
TOOLS=ROOT/"output/windows-build-tools"
OUTPUT=ROOT/"output/kaisip-android"


def run(*args):
    subprocess.run([str(arg) for arg in args],check=True)


def main():
    pinned=json.loads((SOURCE/"upstream.json").read_text(encoding="utf-8"))
    for name,digest in pinned["files"].items():
        path=SOURCE/"assets/upstream"/name
        if hashlib.sha256(path.read_bytes()).hexdigest()!=digest:
            raise ValueError("Pinned upstream asset changed: "+name)
    java=next(TOOLS.glob("jdk/*/bin/java.exe"));sdk=next(TOOLS.glob("platform/*/android.jar"))
    build=next(TOOLS.glob("build-tools/*/aapt.exe")).parent
    OUTPUT.mkdir(parents=True,exist_ok=True)
    classes=OUTPUT/"classes";dex=OUTPUT/"dex";classes.mkdir(exist_ok=True);dex.mkdir(exist_ok=True)
    run(java.with_name("javac.exe"),"-encoding","UTF-8","-source","8","-target","8",
        "-bootclasspath",sdk,"-d",classes,*sorted(SOURCE.glob("*.java")))
    run(java,"-cp",build/"lib/d8.jar","com.android.tools.r8.D8","--min-api","27",
        "--lib",sdk,"--output",dex,*sorted(classes.rglob("*.class")))
    unsigned=OUTPUT/"unsigned.apk";aligned=OUTPUT/"aligned.apk";apk=OUTPUT/"kaisip-android.apk"
    run(build/"aapt.exe","package","-f","-M",SOURCE/"AndroidManifest.xml","-S",SOURCE/"res",
        "-A",SOURCE/"assets","-I",sdk,"-F",unsigned)
    with zipfile.ZipFile(unsigned,"a",zipfile.ZIP_DEFLATED) as archive:archive.write(dex/"classes.dex","classes.dex")
    run(build/"zipalign.exe","-f","4",unsigned,aligned)
    run(java,"-jar",build/"lib/apksigner.jar","sign","--key",TOOLS/"testkey.pk8",
        "--cert",TOOLS/"testkey.x509.pem","--out",apk,aligned)
    run(java,"-jar",build/"lib/apksigner.jar","verify",apk)
    print("KaiSIP APK SHA256:",hashlib.sha256(apk.read_bytes()).hexdigest())


if __name__=="__main__":main()
