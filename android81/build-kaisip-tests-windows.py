"""Build local-only Android KaiSIP instrumentation. Does not install or make calls."""
import importlib.util
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location("kaisip_build",ROOT/"build-kaisip-windows.py")
builder=importlib.util.module_from_spec(spec);spec.loader.exec_module(builder)
run=builder.run;tools=builder.TOOLS
java=next(tools.glob("jdk/*/bin/java.exe"));sdk=next(tools.glob("platform/*/android.jar"));bt=next(tools.glob("build-tools/*/aapt.exe")).parent
out=ROOT/"output/kaisip-checks";out.mkdir(parents=True,exist_ok=True)
classes=out/"classes";dex=out/"dex";classes.mkdir(exist_ok=True);dex.mkdir(exist_ok=True)
run(java.with_name("javac.exe"),"-encoding","UTF-8","-source","8","-target","8","-bootclasspath",sdk,"-classpath",builder.OUTPUT/"classes","-d",classes,ROOT/"kaisip-android/tests/KaiSipChecks.java")
run(java,"-cp",bt/"lib/d8.jar","com.android.tools.r8.D8","--min-api","27","--lib",sdk,"--classpath",builder.OUTPUT/"classes","--output",dex,*sorted(classes.rglob("*.class")))
manifest=out/"AndroidManifest.xml"
manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="ro.n0d3.kaisip.checks"><uses-sdk android:minSdkVersion="27" android:targetSdkVersion="27"/><application android:label="KaiSIP local checks"/><instrumentation android:name="ro.n0d3.kaisip.KaiSipChecks" android:targetPackage="ro.n0d3.kaisip"/></manifest>',encoding="utf-8")
run(bt/"aapt.exe","package","-f","-M",manifest,"-I",sdk,"-F",out/"unsigned.apk")
with zipfile.ZipFile(out/"unsigned.apk","a",zipfile.ZIP_DEFLATED) as z:z.write(dex/"classes.dex","classes.dex")
run(bt/"zipalign.exe","-f","4",out/"unsigned.apk",out/"aligned.apk")
run(java,"-jar",bt/"lib/apksigner.jar","sign","--key",tools/"testkey.pk8","--cert",tools/"testkey.x509.pem","--out",out/"tests.apk",out/"aligned.apk")
