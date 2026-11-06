"""Build isolated Pocket application tests; never installs or connects to a server."""
from pathlib import Path
import subprocess,zipfile,sys
r=Path(__file__).resolve().parent;t=r/'output/windows-build-tools';b=r/'output/pocket-apps-tests';b.mkdir(exist_ok=True);(b/'classes').mkdir(exist_ok=True);(b/'dex').mkdir(exist_ok=True)
j=next((t/'jdk').glob('*/bin/java.exe'));sdk=next((t/'platform').glob('*/android.jar'));bt=next((t/'build-tools').iterdir())
def run(*a):subprocess.run([str(x) for x in a],check=True)
import hashlib,urllib.request
zxing=r/'output/pocket-zxing-tests.jar'
if not zxing.exists():zxing.write_bytes(urllib.request.urlopen('https://repo.maven.apache.org/maven2/com/google/zxing/core/3.5.3/core-3.5.3.jar',timeout=30).read())
assert hashlib.sha256(zxing.read_bytes()).hexdigest()=='8d8064c1636fdaef7189dd9055c7d59950a8940a12f2293956446ec3c109fd82','Test decoder hash mismatch'
sys.path.insert(0,str(r));from importlib.machinery import SourceFileLoader
prepare=SourceFileLoader('prepare',str(r/'prepare-pocket-dependencies.py')).load_module()
with zipfile.ZipFile(r/'output/pocket-zxing-tests.jar') as src,zipfile.ZipFile(b/'zxing.jar','w',zipfile.ZIP_DEFLATED) as dst:
 for i in src.infolist():
  if i.filename.endswith('.class') and not i.filename.startswith('META-INF/') and not i.filename.endswith('module-info.class'):dst.writestr(i,prepare.strip_parameters(src.read(i.filename)))
m=b/'AndroidManifest.xml';m.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="ro.leo.visual.pocketapptests"><uses-sdk android:minSdkVersion="27" android:targetSdkVersion="27"/><application android:label="Leo local pocket checks"/><instrumentation android:name="ro.leo.visual.PocketApplicationsTests" android:targetPackage="ro.leo.visual"/></manifest>')
run(bt/'aapt.exe','package','-f','-M',m,'-I',sdk,'-F',b/'base.apk')
cp=str(r/'output/pocket-apps-build/leo-visual-classes.jar')+';'+str(b/'zxing.jar')
run(j.with_name('javac.exe'),'-encoding','UTF-8','-source','1.8','-target','1.8','-bootclasspath',sdk,'-classpath',cp,'-d',b/'classes',r/'visual-shell/tests/PocketApplicationsTests.java')
run(j,'-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','27','--lib',sdk,'--classpath',r/'output/pocket-apps-build/leo-visual-classes.jar','--output',b/'dex',b/'zxing.jar',*list((b/'classes').rglob('*.class')))
with zipfile.ZipFile(b/'base.apk','a',zipfile.ZIP_DEFLATED) as z:z.write(b/'dex/classes.dex','classes.dex')
run(bt/'zipalign.exe','-f','4',b/'base.apk',b/'aligned.apk')
run(j,'-jar',bt/'lib/apksigner.jar','sign','--key',t/'testkey.pk8','--cert',t/'testkey.x509.pem','--out',b/'tests.apk',b/'aligned.apk')
