"""Incremental Windows APK builder using the verified local APK/class checkpoint; never installs."""
from pathlib import Path
import subprocess,zipfile,hashlib,shutil,json
root=Path(__file__).resolve().parent;out=root/'output';tools=out/'windows-build-tools';build=out/'pocket-apps-build';build.mkdir(exist_ok=True)
subprocess.run(['python','-X','utf8',str(root/'prepare-pocket-dependencies.py'),'--output',str(out/'pocket-dependencies')],check=True)
java=next((tools/'jdk').glob('*/bin/java.exe'));javac=java.with_name('javac.exe');jar=java.with_name('jar.exe');sdk=next((tools/'platform').glob('*/android.jar'));bt=next((tools/'build-tools').iterdir())
def run(*args):subprocess.run([str(x) for x in args],check=True)
# Keep the known built APK/class archive untouched until the candidate is verified.
base=out/'leo-visual.apk';classes=out/'leo-visual-classes.jar';saved=build/'baseline.apk';cached=build/'baseline-classes.jar'
if not saved.exists():shutil.copy2(base,saved);shutil.copy2(classes,cached)
newclasses=build/'classes';assert newclasses.resolve().parent==build.resolve() and build.resolve().parent==out.resolve()
if newclasses.exists():shutil.rmtree(newclasses)
newclasses.mkdir(exist_ok=True);(build/'d8').mkdir(exist_ok=True)
changed=['PodcastDownloads', 'PodcastDownloadReceiver', 'PodcastTransferService', 'PlaybackService', 'IcyMetadata', 'PocketStore', 'PocketActivity', 'PocketFeeds', 'FeedsActivity', 'StreamRadioActivity', 'PocketTable', 'LexiconActivity', 'StudyActivity', 'PocketZip', 'ZipActivity', 'ExpenseBook', 'ExpensesActivity', 'MetronomeService', 'MetronomeActivity', 'PocketQr', 'QrActivity', 'PocketSsh', 'SshActivity', 'AppCatalog', 'MainActivity', 'MediaPanel', 'MusicTransport', 'KeypadText', 'MusicFace', 'MediaActivity', 'NowPlayingActivity', 'GlobalSearchActivity', 'PersonalizationActivity', 'KeypadDialog', 'LeoPointerService', 'GlobalMenuModel', 'IconArt', 'AppBadge', 'FilesActivity', 'EnglishCatalog']
run(javac,'-encoding','UTF-8','-source','1.8','-target','1.8','-bootclasspath',sdk,'-classpath',str(cached)+';'+str(out/'pocket-dependencies/jsch.jar'),'-d',newclasses,*[root/'visual-shell'/(x+'.java') for x in changed if x!='EnglishCatalog'],root/'localization/EnglishCatalog.java',*sorted((root/'third-party/pocket').glob('*.java')))
merged=build/'leo-visual-classes.jar'
with zipfile.ZipFile(cached) as src,zipfile.ZipFile(merged,'w',zipfile.ZIP_DEFLATED) as dst:
 for i in src.infolist():
  if i.filename.startswith(('com/jcraft/jsch/','io/nayuki/qrcodegen/')):continue
  short=i.filename.split('/')[-1]
  if (i.filename.startswith('ro/leo/visual/') or i.filename.startswith('ro/leo/i18n/')) and any(short==n+'.class' or short.startswith(n+'$') for n in changed):continue
  dst.writestr(i,src.read(i.filename))
 for p in newclasses.rglob('*.class'):dst.write(p,p.relative_to(newclasses).as_posix())
 with zipfile.ZipFile(out/'pocket-dependencies/jsch.jar') as library:
  for i in library.infolist():
   if i.filename.endswith('.class'):dst.writestr(i,library.read(i.filename))
with zipfile.ZipFile(saved) as z:(build/'baseline.dex').write_bytes(z.read('classes.dex'))
helper=build/'KeepDependencies.java';helper.write_text('''import java.io.*;import java.util.*;import org.jf.dexlib2.*;import org.jf.dexlib2.iface.*;import org.jf.dexlib2.immutable.*;
public class KeepDependencies {public static void main(String[] a)throws Exception{DexFile d=DexFileFactory.loadDexFile(new File(a[0]),Opcodes.forApi(27));ArrayList<ClassDef> keep=new ArrayList<>();for(ClassDef c:d.getClasses())if(!c.getType().startsWith("Lro/leo/")&&!c.getType().startsWith("Lcom/jcraft/jsch/")&&!c.getType().startsWith("Lio/nayuki/qrcodegen/"))keep.add(c);if(keep.isEmpty())throw new IllegalStateException("No retained dependency classes");DexFileFactory.writeDexFile(a[1],new ImmutableDexFile(Opcodes.forApi(27),keep));System.out.println("Retained dependency classes: "+keep.size());}}
''',encoding='utf-8')
cp=';'.join(str(tools/n) for n in ['dexlib2.jar','guava.jar','jsr305.jar'])
run(javac,'-cp',cp,'-d',build,helper);run(java,'-cp',str(build)+';'+cp,'KeepDependencies',build/'baseline.dex',build/'dependencies.dex')
run(java,'-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','27','--lib',sdk,'--output',build/'d8',merged)
assert len(list((build/'d8').glob('classes*.dex')))==1,'Unexpected multidex output'
shutil.copy2(build/'d8/classes.dex',build/'project.dex')
merge=build/'MergeDex.java';merge.write_text('''import java.io.*;import java.util.*;import org.jf.dexlib2.*;import org.jf.dexlib2.iface.*;import org.jf.dexlib2.immutable.*;
public class MergeDex {public static void main(String[] a)throws Exception{ArrayList<ClassDef> all=new ArrayList<>();HashSet<String> names=new HashSet<>();int retained=0;for(int i=0;i<2;i++)for(ClassDef c:DexFileFactory.loadDexFile(new File(a[i]),Opcodes.forApi(27)).getClasses()){if(!names.add(c.getType()))throw new IllegalStateException("Duplicate "+c.getType());all.add(c);if(i==1)retained++;}if(retained!=59)throw new IllegalStateException("Expected 59 baseline dependency classes, got "+retained);DexFileFactory.writeDexFile(a[2],new ImmutableDexFile(Opcodes.forApi(27),all));Set<String> checked=new HashSet<>();for(ClassDef c:DexFileFactory.loadDexFile(new File(a[2]),Opcodes.forApi(27)).getClasses())checked.add(c.getType());if(!checked.equals(names))throw new IllegalStateException("DEX class coverage mismatch");System.out.println("Verified "+checked.size()+" classes including all "+retained+" dependency classes");}}
''',encoding='utf-8')
run(javac,'-cp',cp,'-d',build,merge);run(java,'-cp',str(build)+';'+cp,'MergeDex',build/'project.dex',build/'dependencies.dex',build/'classes.dex')
unsigned=build/'unsigned.apk'
run(bt/'aapt.exe','package','-f','-M',root/'visual-shell/AndroidManifest.xml','-S',root/'visual-shell/res','-A',root/'visual-shell/assets','-I',sdk,'-F',unsigned)
with zipfile.ZipFile(unsigned,'a',zipfile.ZIP_DEFLATED) as dst:dst.write(build/'classes.dex','classes.dex')
run(bt/'zipalign.exe','-f','4',unsigned,build/'aligned.apk')
run(java,'-jar',bt/'lib/apksigner.jar','sign','--key',tools/'testkey.pk8','--cert',tools/'testkey.x509.pem','--out',build/'leo-visual.apk',build/'aligned.apk')
run(java,'-jar',bt/'lib/apksigner.jar','verify','--verbose','--print-certs',build/'leo-visual.apk')
print('Candidate SHA256:',hashlib.sha256((build/'leo-visual.apk').read_bytes()).hexdigest())
