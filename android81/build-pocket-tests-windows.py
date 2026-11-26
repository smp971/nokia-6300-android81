"""Build isolated Pocket application tests; never installs or connects to a server."""
from pathlib import Path
import argparse,subprocess,zipfile,sys
parser=argparse.ArgumentParser(description=__doc__)
modes=parser.add_mutually_exclusive_group()
modes.add_argument("--app-data",action="store_true",help="build the isolated app-data backup tests")
modes.add_argument("--ui-theme",action="store_true",help="build theme inheritance and live page checks")
modes.add_argument("--ui-polish",action="store_true",help="build native dialog, form and clock checks")
modes.add_argument("--daily-polish",action="store_true",help="build daily UI, private Home boundary and display lifecycle checks")
modes.add_argument("--web-browser",action="store_true",help="build real graphical browser HTTP and DOM checks")
modes.add_argument("--browser-state",action="store_true",help="build private browser records and battery transition checks")
modes.add_argument("--shell-optimization",action="store_true",help="build typography, incremental rows and UI memory/lifecycle checks")
modes.add_argument("--update-recovery",action="store_true",help="build isolated interrupted app-update journal checks")
modes.add_argument("--home-standby",action="store_true",help="build Home/standby rendering, presets and keypad checks")
modes.add_argument("--unlock-sequence",action="store_true",help="build isolated softkey/OK then star unlock checks")
modes.add_argument("--webui-access",action="store_true",help="build persistent password WebUI and native-control checks")
modes.add_argument("--webui",action="store_true",help="build isolated HTTPS WebUI model and transfer checks")
modes.add_argument("--launcher-folders",action="store_true",help="build isolated folder projection, persistence and keypad navigation checks")
modes.add_argument("--messages-ui",action="store_true",help="build synthetic message tabs, draft/editor and theme checks without sending SMS")
modes.add_argument("--showcase",action="store_true",help="build the local documentation screenshot capture fixture")
args=parser.parse_args()
app_data,ui_theme=args.app_data,args.ui_theme
if args.webui_access:
 test,package,directory="WebUiAccessTests","ro.leo.visual.webuiaccesschecks","webui-access-tests"
elif args.webui:
 test,package,directory="WebUiTests","ro.leo.visual.webuichecks","webui-tests"
elif args.launcher_folders:
 test,package,directory="LauncherFoldersTests","ro.leo.visual.launcherfolderchecks","launcher-folders-tests"
elif args.messages_ui:
 test,package,directory="MessagesUiTests","ro.leo.visual.messagesuichecks","messages-ui-tests"
elif args.unlock_sequence:
 test,package,directory="UnlockSequenceTests","ro.leo.visual.unlockchecks","unlock-sequence-tests"
elif args.showcase:
 test,package,directory="ShowcaseCapture","ro.leo.visual.showcasechecks","showcase-capture"
elif args.home_standby:
 test,package,directory="HomeStandbyTests","ro.leo.visual.homestandbychecks","home-standby-tests"
elif args.update_recovery:
 test,package,directory="UpdateRecoveryTests","ro.leo.visual.updaterecoverychecks","update-recovery-tests"
elif args.shell_optimization:
 test,package,directory="ShellOptimizationTests","ro.leo.visual.shelloptimizationchecks","shell-optimization-tests"
elif args.browser_state:
 test,package,directory="BrowserStateDeviceTests","ro.leo.visual.browserstatechecks","browser-state-tests"
elif args.web_browser:
 test,package,directory="WebBrowserDeviceTests","ro.leo.visual.webbrowserchecks","web-browser-tests"
elif args.daily_polish:
 test,package,directory="KeyShellDailyUiTests","ro.leo.visual.dailychecks","daily-polish-tests"
elif args.ui_polish:
 test,package,directory="UiPolishTests","ro.leo.visual.uipolishchecks","ui-polish-tests"
elif ui_theme:
 test,package,directory='UiThemeTests','ro.leo.visual.uithemechecks','ui-theme-tests'
elif app_data:
 test,package,directory='AppDataBackupTests','ro.leo.visual.backupchecks','app-data-backup-tests'
else:
 test,package,directory='PocketApplicationsTests','ro.leo.visual.pocketapptests','pocket-apps-tests'
r=Path(__file__).resolve().parent;t=r/'output/windows-build-tools';b=r/'output'/directory;b.mkdir(exist_ok=True);(b/'classes').mkdir(exist_ok=True);(b/'dex').mkdir(exist_ok=True)
j=next((t/'jdk').glob('*/bin/java.exe'));sdk=next((t/'platform').glob('*/android.jar'));bt=next((t/'build-tools').iterdir())
def run(*a):subprocess.run([str(x) for x in a],check=True)
dependencies=[]
if not app_data and not ui_theme and not args.ui_polish and not args.daily_polish and not args.web_browser and not args.browser_state and not args.shell_optimization and not args.update_recovery and not args.home_standby and not args.showcase and not args.unlock_sequence and not args.messages_ui and not args.launcher_folders and not args.webui and not args.webui_access:
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
probe='<activity android:name="ro.leo.visual.ExternalHomeProbe" android:exported="true" />' if args.daily_polish else ''
m=b/'AndroidManifest.xml';m.write_text(f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{package}"><uses-sdk android:minSdkVersion="27" android:targetSdkVersion="27"/><application android:label="Leo local checks">{probe}</application><instrumentation android:name="ro.leo.visual.{test}" android:targetPackage="ro.leo.visual"/></manifest>',encoding='utf-8')
values=b/'res/values';values.mkdir(parents=True,exist_ok=True)
(values/'strings.xml').write_text('<resources><string name="fixture_label">Leo local checks</string></resources>',encoding='utf-8')
run(bt/'aapt.exe','package','-f','-M',m,'-S',b/'res','-I',sdk,'-F',b/'base.apk')
classes=r/'output/pocket-apps-build/leo-visual-classes.jar'
cp=';'.join(map(str,[classes,*dependencies]))
run(j.with_name('javac.exe'),'-encoding','UTF-8','-source','1.8','-target','1.8','-bootclasspath',sdk,'-classpath',cp,'-d',b/'classes',r/f'visual-shell/tests/{test}.java',*([r/'visual-shell/tests/WebUiTests.java'] if args.webui_access else []),*([r/'visual-shell/tests/ExternalHomeProbe.java'] if args.daily_polish else []))
run(j,'-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','27','--lib',sdk,'--classpath',classes,'--output',b/'dex',*dependencies,*sorted((b/'classes').rglob('*.class')))
with zipfile.ZipFile(b/'base.apk','a',zipfile.ZIP_DEFLATED) as z:z.write(b/'dex/classes.dex','classes.dex')
run(bt/'zipalign.exe','-f','4',b/'base.apk',b/'aligned.apk')
run(j,'-jar',bt/'lib/apksigner.jar','sign','--key',t/'testkey.pk8','--cert',t/'testkey.x509.pem','--out',b/'tests.apk',b/'aligned.apk')
