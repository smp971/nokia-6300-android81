"""Rebuild only the app-local keypad helper in verified existing system APKs.

This Windows fallback preserves app code/resources and requires original APK
backups, portable SDK/JDK tools and matching AOSP development signing keys.
It does not build framework.jar or change the boot classpath.
"""
from pathlib import Path
import argparse,base64,hashlib,json,re,subprocess,tempfile,zipfile
root=Path(__file__).resolve().parent
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--baseline-dir',type=Path,required=True);p.add_argument('--tools',type=Path,default=root/'output/windows-build-tools');p.add_argument('--output-dir',type=Path,default=root/'output/framework-keypad');args=p.parse_args()
t=args.tools.resolve();baseline=args.baseline_dir.resolve();output=args.output_dir.resolve();output.mkdir(exist_ok=True)
b=Path(tempfile.mkdtemp(prefix='system-keypad-build-',dir=root/'output'));classes=b/'classes';classes.mkdir()
j=next((t/'jdk').glob('*/bin/java.exe'));sdk=next((t/'platform').glob('*/android.jar'));bt=next((t/'build-tools').iterdir())
def run(*a):subprocess.run([str(x) for x in a],check=True)
def cert(apk):
 text=subprocess.check_output([str(j),'-jar',str(bt/'lib/apksigner.jar'),'verify','--print-certs',str(apk)],text=True)
 matches=re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)',text)
 if len(matches)!=1:raise RuntimeError('Expected one verified APK signer')
 return matches[0]
keys={}
for key in ['platform','testkey']:
 pem=(t/(key+'.x509.pem')).read_text();der=base64.b64decode(''.join(line for line in pem.splitlines() if not line.startswith('---')));keys[hashlib.sha256(der).hexdigest()]=key
manifest=json.loads((baseline/'manifest.json').read_text(encoding='utf-8'))
run(j.with_name('javac.exe'),'-encoding','UTF-8','-source','1.8','-target','1.8','-bootclasspath',sdk,'-d',classes,root/'framework-keypad/LeoKeypadNavigation.java')
run(j,'-jar',bt/'lib/dx.jar','--dex','--output='+str(b/'helper.dex'),classes)
helper=b/'ReplaceKeypad.java';helper.write_text('''import java.io.*;import java.util.*;import org.jf.dexlib2.*;import org.jf.dexlib2.iface.*;import org.jf.dexlib2.immutable.*;
public class ReplaceKeypad{static boolean helper(String name){return name.equals("Lcom/android/internal/policy/LeoKeypadNavigation;")||name.startsWith("Lcom/android/internal/policy/LeoKeypadNavigation$");}
public static void main(String[] a)throws Exception{DexFile original=DexFileFactory.loadDexFile(new File(a[0]),Opcodes.forApi(27)),patch=DexFileFactory.loadDexFile(new File(a[1]),Opcodes.forApi(27));ArrayList<ClassDef> result=new ArrayList<>();TreeSet<String> before=new TreeSet<>(),after=new TreeSet<>();int removed=0;
for(ClassDef c:original.getClasses()){before.add(c.getType());if(helper(c.getType()))removed++;else result.add(c);}if(removed==0)throw new IllegalStateException("Helper absent in selected DEX");
for(ClassDef c:patch.getClasses()){if(!helper(c.getType()))throw new IllegalStateException("Unrelated patch class");result.add(c);}for(ClassDef c:result)if(!after.add(c.getType()))throw new IllegalStateException("Duplicate class");before.removeIf(n->helper(n));after.removeIf(n->helper(n));if(!before.equals(after))throw new IllegalStateException("Unrelated class set changed");
DexFileFactory.writeDexFile(a[2],new ImmutableDexFile(Opcodes.forApi(27),result));System.out.println("Result classes: "+result.size()+"; replaced "+removed+" helper classes");}}
''',encoding='utf-8')
cp=';'.join(str(t/n) for n in ['dexlib2.jar','guava.jar','jsr305.jar']);run(j.with_name('javac.exe'),'-cp',cp,'-d',b,helper)
report={}
for package,row in manifest.items():
 source=baseline/row['file'];assert hashlib.sha256(source.read_bytes()).hexdigest()==row['sha256'],'Backup changed';signer=cert(source);assert signer in keys,'No matching development signing key';key=keys[signer]
 unsigned=b/('unsigned-'+row['file']);changed=0
 with zipfile.ZipFile(source) as src,zipfile.ZipFile(unsigned,'w',zipfile.ZIP_DEFLATED) as dst:
  for info in src.infolist():
   if info.filename.startswith('META-INF/'):continue
   data=src.read(info.filename)
   if re.fullmatch(r'classes[0-9]*\.dex',info.filename) and b'Lcom/android/internal/policy/LeoKeypadNavigation;' in data:
    old=b/'old.dex';new=b/'new.dex';old.write_bytes(data);run(j,'-cp',str(b)+';'+cp,'ReplaceKeypad',old,b/'helper.dex',new);data=new.read_bytes();changed+=1
   dst.writestr(info,data)
 assert changed==1,'Expected exactly one helper DEX per app'
 aligned=b/('aligned-'+row['file']);candidate=output/row['file'];run(bt/'zipalign.exe','-f','4',unsigned,aligned)
 run(j,'-jar',bt/'lib/apksigner.jar','sign','--key',t/(key+'.pk8'),'--cert',t/(key+'.x509.pem'),'--out',candidate,aligned)
 assert cert(candidate)==signer,'Signer changed'
 with zipfile.ZipFile(source) as old,zipfile.ZipFile(candidate) as new:
  preserved=[n for n in old.namelist() if not n.startswith('META-INF/') and not re.fullmatch(r'classes[0-9]*\.dex',n)]
  assert all(old.read(n)==new.read(n) for n in preserved),'Non-code content changed'
 report[package]={'before':row['sha256'],'after':hashlib.sha256(candidate.read_bytes()).hexdigest(),'signer':signer,'preserved_entries':len(preserved)}
 print('Built',package,flush=True)
(output/'keypad-incremental-build.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
