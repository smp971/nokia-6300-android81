"""WSL: collect native graphics dependency closure into a volatile test runtime."""
from pathlib import Path
import hashlib,io,json,re,subprocess,tarfile
HERE=Path(__file__).resolve().parent;OUT=HERE/'output'
ROOT=Path('/root/android81-leo/src/out/target/product/leo')
selected={}
def normalized(p):
    name=p.relative_to(ROOT).as_posix()
    if name.startswith('root/'):return name[5:]
    return 'vendor/'+name[len('system/vendor/'):] if name.startswith('system/vendor/') else name
def add(p):
    if not p.exists():raise FileNotFoundError(p)
    name=normalized(p)
    if name in selected:return
    selected[name]=p
    if p.is_symlink():
        add(p.resolve());return
    if p.is_file() and p.read_bytes()[:4]==b'\x7fELF':
        text=subprocess.run(['readelf','-d',str(p)],check=True,capture_output=True,text=True).stdout
        for dep in re.findall(r'\(NEEDED\).*?\[(.*?)\]',text):
            choices=[p.parent/dep,ROOT/'system/lib'/dep,ROOT/'vendor/lib'/dep,ROOT/'system/vendor/lib'/dep,ROOT/'system/lib/vndk'/dep,ROOT/'system/lib/vndk-sp'/dep]
            found=next((x for x in choices if x.exists()),None)
            if not found:raise RuntimeError(f'{name}: missing {dep}')
            add(found)
for name in ('leo_graphics_probe','leo_surface_test','surfaceflinger','servicemanager','hwservicemanager','logd','logcat','dumpsys','service','linker','getprop','setprop','sh'):
    add(ROOT/'system/bin'/name)
for pattern in ('*EGL_swiftshader.so','*GLESv1_CM_swiftshader.so','*GLESv2_swiftshader.so','gralloc.default.so',
                'android.hardware.graphics.composer@2.1-impl.so','android.hardware.graphics.allocator@2.0-impl.so','android.hardware.graphics.mapper@2.0-impl.so',
                'android.hardware.graphics.composer@2.1-service','android.hardware.graphics.allocator@2.0-service'):
    matches=[p for prefix in ('system','vendor') for p in (ROOT/prefix).rglob(pattern) if '/lib64/' not in str(p)]
    assert matches,pattern
    for p in matches:add(p)
hwc=ROOT/'system/vendor/lib/hw/hwcomposer.leo.so'
add(hwc)
selected['vendor/lib/hw/hwcomposer.default.so']=hwc
for path in ('system/build.prop','vendor/build.prop','system/etc/ld.config.txt','system/etc/public.libraries.txt','system/manifest.xml','system/compatibility_matrix.xml','vendor/manifest.xml'):
    p=ROOT/path
    if p.is_file():add(p)
for p in (ROOT/'root').glob('*contexts'):
    if p.is_file():add(p)
dest=OUT/'graphics-runtime.tar.gz'
with tarfile.open(dest,'w:gz',format=tarfile.USTAR_FORMAT) as tar:
    for name in ('dev','proc','sys','tmp','data','data/misc','data/misc/logd','data/local','data/local/tmp','system','vendor'):
        t=tarfile.TarInfo(name);t.type=tarfile.DIRTYPE;t.mode=0o755;tar.addfile(t)
    t=tarfile.TarInfo('system/vendor');t.type=tarfile.SYMTYPE;t.linkname='/vendor';t.mode=0o777;tar.addfile(t)
    for name,p in sorted(selected.items()):
        t=tarfile.TarInfo(name);t.mode=p.stat().st_mode&0o777;t.uid=t.gid=0;t.mtime=0
        if p.is_symlink():
            target=str(p.readlink())
            if target.startswith('/system/vendor/'):target='/vendor/'+target[len('/system/vendor/'):]
            t.type=tarfile.SYMTYPE;t.linkname=target;tar.addfile(t)
        else:
            body=p.read_bytes();t.size=len(body);tar.addfile(t,io.BytesIO(body))
report={'archive_bytes':dest.stat().st_size,'sha256':hashlib.sha256(dest.read_bytes()).hexdigest(),
        'installed_bytes':sum(p.stat().st_size for p in selected.values() if not p.is_symlink()),'files':sorted(selected),
        'target_directory':'/tmp/leo-graphics','persistent_partition_writes':False}
(OUT/'graphics-runtime.json').write_text(json.dumps(report,indent=2));print(json.dumps({k:v for k,v in report.items() if k!='files'},indent=2))
