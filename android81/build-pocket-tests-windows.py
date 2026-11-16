"""Build isolated Pocket application tests; never installs or connects to a server."""
from pathlib import Path
import argparse,subprocess,zipfile,sys
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument("--app-data",action="store_true",help="build the isolated app-data backup tests")
app_data=parser.parse_args().app_data
r=Path(__file__).resolve().parent;t=r/'output/windows-build-tools';b=r/'output'/('app-data-backup-tests' if app_data else 'pocket-apps-tests');b.mkdir(exist_ok=True);(b/'classes').mkdir(exist_ok=True);(b/'dex').mkdir(exist_ok=True)
j=next((t/'jdk').glob('*/bin/java.exe'));sdk=next((t/'platform').glob('*/android.jar'));bt=next((t/'build-tools').iterdir())
def run(*a):subprocess.run([str(x) for x in a],check=True)
dependencies=[]
if not app_data:
 import hashlib,urllib.request
 zxing=r/'output/pocket-zxing-tests.jar'
 if not zxing.exists():zxing.write_bytes(urllib.request.urlopen('https://repo.maven.apache.org/maven2/com/google/zxing/core/3.5.3/core-3.5.3.jar',timeout=30).read())
 assert hashlib.sha256(zxing.read_bytes()).hexdigest()=='8d8064c1636fdaef7189dd9055c7d59950a8940a12f2293956446ec3c109fd82','Test decoder hash mismatch'
 sys.path.insert(0,str(r));from importlib.machinery import SourceFileLoader
 prepare=SourceFileLoader('prepare',str(r/'prepare-pocket-dependencies.py')).load_module()
 with zipfile.ZipFile(r/'output/pocket-zxing-tests.jar') as src,zipfile.ZipFile(b/'zxing.jar','w',zipfile.ZIP_DEFLATED) as dst:
  for i in src.infolist():
   if i.filename.endswith('.class') and not i.filename.startswith('META-INF/') and not i.filename.endswith('module-info.class'):dst.writestr(i,prepare.strip_parameters(src.read(i.filename)))
 dependencies=[b/'zxing.jar']
test='AppDataBackupTests' if app_data else 'PocketApplicationsTests'
package='ro.leo.visual.backupchecks' if app_data else 'ro.leo.visual.pocketapptests'
m=b/'AndroidManifest.xml';m.write_text(f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{package}"><uses-sdk android:minSdkVersion="27" android:targetSdkVersion="27"/><application android:label="Leo local checks"/><instrumentation android:name="ro.leo.visual.{test}" android:targetPackage="ro.leo.visual"/></manifest>',encoding='utf-8')
values=b/'res/values';values.mkdir(parents=True,exist_ok=True)
(values/'strings.xml').write_text('<resources><string name="fixture_label">Leo local checks</string></resources>',encoding='utf-8')
run(bt/'aapt.exe','package','-f','-M',m,'-S',b/'res','-I',sdk,'-F',b/'base.apk')
classes=r/'output/pocket-apps-build/leo-visual-classes.jar'
cp=';'.join(map(str,[classes,*dependencies]))
run(j.with_name('javac.exe'),'-encoding','UTF-8','-source','1.8','-target','1.8','-bootclasspath',sdk,'-classpath',cp,'-d',b/'classes',r/f'visual-shell/tests/{test}.java')
run(j,'-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','27','--lib',sdk,'--classpath',classes,'--output',b/'dex',*dependencies,*sorted((b/'classes').rglob('*.class')))
with zipfile.ZipFile(b/'base.apk','a',zipfile.ZIP_DEFLATED) as z:z.write(b/'dex/classes.dex','classes.dex')
run(bt/'zipalign.exe','-f','4',b/'base.apk',b/'aligned.apk')
run(j,'-jar',bt/'lib/apksigner.jar','sign','--key',t/'testkey.pk8','--cert',t/'testkey.x509.pem','--out',b/'tests.apk',b/'aligned.apk')
