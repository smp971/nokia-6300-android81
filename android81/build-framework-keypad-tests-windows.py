"""Build the standalone Android keypad fixtures with portable Windows SDK tools."""
from pathlib import Path
import argparse,subprocess,tempfile,zipfile
root=Path(__file__).resolve().parent
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--tools',type=Path,default=root/'output/windows-build-tools');args=p.parse_args();tools=args.tools.resolve()
java=next((tools/'jdk').glob('*/bin/java.exe'));sdk=next((tools/'platform').glob('*/android.jar'));bt=next((tools/'build-tools').iterdir())
build=Path(tempfile.mkdtemp(prefix='keypad-checks-',dir=root/'output'));classes=build/'classes';classes.mkdir()
def run(*a):subprocess.run([str(x) for x in a],check=True)
run(bt/'aapt.exe','package','-f','-M',root/'framework-keypad/tests/AndroidManifest.xml','-I',sdk,'-F',build/'base.apk')
run(java.with_name('javac.exe'),'-encoding','UTF-8','-source','1.8','-target','1.8','-bootclasspath',sdk,'-d',classes,root/'framework-keypad/LeoKeypadNavigation.java',*sorted((root/'framework-keypad/tests').glob('*.java')))
run(java,'-jar',bt/'lib/dx.jar','--dex','--output='+str(build/'classes.dex'),classes)
with zipfile.ZipFile(build/'base.apk','a',zipfile.ZIP_DEFLATED) as z:z.write(build/'classes.dex','classes.dex')
run(bt/'zipalign.exe','-f','4',build/'base.apk',build/'aligned.apk')
run(java,'-jar',bt/'lib/apksigner.jar','sign','--key',tools/'testkey.pk8','--cert',tools/'testkey.x509.pem','--out',root/'output/leo-system-keypad-tests.apk',build/'aligned.apk')
print('Built standalone keypad tests:',root/'output/leo-system-keypad-tests.apk')
