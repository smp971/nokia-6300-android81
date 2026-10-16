"""Early ADB gate before Android init; stock recovery header/kernel and RAM only."""
from pathlib import Path
import argparse,gzip,hashlib,json,struct,re,tarfile
parser=argparse.ArgumentParser()
parser.add_argument('--ui',action='store_true',help='Embed native UI test and start init automatically')
parser.add_argument('--graphics',action='store_true',help='Embed tested RAM graphics runtime and automatic startup')
parser.add_argument('--framework',action='store_true',help='Boot full framework from dedicated SD image files')
parser.add_argument('--internal',action='store_true',help='Boot Android from validated system/userdata partitions')
args=parser.parse_args()
if args.internal:args.framework=True
if args.graphics:args.ui=True
here=Path(__file__).resolve().parent;out=here/'output'
def digest(b):return hashlib.sha256(b).hexdigest()
def parse(b):
    assert b[:8]==b'ANDROID!'
    k,_,r,_,s,_,_,p=struct.unpack_from('<8I',b,8);assert p==2048 and s==0
    off=p+(k+p-1)//p*p;c=gzip.decompress(b[off:off+r]);i=0;e={}
    while True:
        assert c[i:i+6]==b'070701'
        f=[int(c[i+6+8*j:i+14+8*j],16) for j in range(13)]
        n=c[i+110:i+109+f[11]].decode();a=(i+110+f[11]+3)&~3
        if n=='TRAILER!!!':return k,r,p,off,e
        assert n not in e;e[n]=(f,c[a:a+f[6]]);i=(a+f[6]+3)&~3
stock=(here/('backup-live/boot-device.img' if args.internal else 'backup-live/recovery-device.img')).read_bytes()
saved=json.loads((here/'backup-live/checksums.json').read_text())
assert digest(stock)==saved['boot' if args.internal else 'recovery']['sha256']
probe=(out/'boot-ramdisk-probe.img').read_bytes()
assert digest(probe)=='8efb73d20c085d731a07d55cfe4ed15bbe2bcf279c7da1ee714357d59bfe1b3f'
k,r,page,off,stockentries=parse(stock)
pk,_,pp,_,e=parse(probe)
assert probe[pp:pp+pk]==stock[page:page+k]
def put(n,b,mode=0o100755):
    e[n]=([0,mode,0,0,1,0,len(b),0,0,0,0,len(n.encode())+1,0],b)
if args.framework:
    assert not args.ui and not args.graphics
    with tarfile.open(out/'framework-root.tar') as root:
        for member in root:
            name=member.name.removeprefix('./').rstrip('/')
            if name in ('','.'):continue
            assert not name.startswith('/') and '..' not in name.split('/')
            if member.issym():put(name,member.linkname.encode(),0o120777)
            elif member.isdir():put(name,b'',0o40000|member.mode)
            elif member.isfile():put(name,root.extractfile(member).read(),0o100000|member.mode)
            else:raise RuntimeError('Unexpected framework root entry: '+name)
    assert b'BOOTCLASSPATH /system/framework/' in e['init.environ.rc'][1]
    assert b'ro.zygote=zygote32' in e['default.prop'][1]
    assert 'init.zygote32.rc' in e
bootstrap=(out/('leo-diag-bootstrap-framework' if args.framework else 'leo-diag-bootstrap')).read_bytes();init=(out/'init-android81-hold').read_bytes()
if args.framework:assert b'framework image mounting failed' in bootstrap
assert b'LEO_DIAG: init panic requested' in init
assert b'USB setup attempted' in bootstrap
for b in [bootstrap,init]:
    assert b[:6]==b'\x7fELF\x01\x01' and struct.unpack_from('<H',b,18)[0]==40
    phoff=struct.unpack_from('<I',b,28)[0];sz,num=struct.unpack_from('<HH',b,42)
    assert all(struct.unpack_from('<I',b,phoff+i*sz)[0] not in (2,3) for i in range(num))
put('init',bootstrap);put('init.android',init)
# AOSP IsRecoveryMode() checks existence of /sbin/recovery. This ramdisk-only
# probe is booted from recovery and must not mount the vendor DT fstab entry.
put('sbin/recovery',b'# Ramdisk-only diagnostic recovery marker.\n',0o100644)
adbd=(out/'boot-root-adbd').read_bytes()
assert digest(adbd)=='a44268d77f41d332e1e4fd69bbf345761210f701936c891eee589f849c12addd'
early_adbd=(out/'adbd-android81-early').read_bytes()
assert b'LEO_EARLY_ADB' in early_adbd
put('system/bin/adbd',early_adbd)
put('system/bin/sh.android81',e['system/bin/sh'][1])
busybox=(out/'recovery-shell-candidate').read_bytes()
assert digest(busybox)=='eab7d79401911369d90960e44d4148c6b8cb082239e8c63b1beafcf8a02c1fb1'
put('system/bin/sh',busybox)
for n in ('busybox','cat','ls','id','dmesg','grep','uname','md5sum','ps','head','tail','mount','umount','mkdir','touch'):
    put('system/bin/'+n,b'/system/bin/sh',0o120777)
put('tmp',b'',0o40755)
rc=e['init.rc'][1]
for name in (b'init.usb.rc',b'init.usb.configfs.rc'):
    rc=rc.replace(b'import /'+name,b'# diagnostic: early bootstrap owns USB; import /'+name)
if args.framework:
    # A full product can ship an install-recovery script; never launch it here.
    rc=re.sub(rb'(?m)^service flash_recovery .*\n(?:[ \t].*\n|\n)*',b'# Recovery rewrite service excluded from SD diagnostic.\n',rc)
    rc=re.sub(rb'(?m)^[ \t]*exec_start update_verifier[^\n]*',b'    # SD diagnostic: no slot verification or slot metadata writes',rc)
    assert b'service flash_recovery ' not in rc
put('init.rc',rc,0o100750)
put('init.qcom.rc',b'''# Diagnostic: never mount persistent partitions or reconfigure early USB.
on fs
    mount tmpfs tmpfs /data mode=0771,uid=1000,gid=1000
on boot
    write /tmp/leo-stage android-init-boot-action
''',0o100750)
if args.ui:
    ui=(out/'leo-diag-ui').read_bytes()
    assert ui[:6]==b'\x7fELF\x01\x01' and b'NATIVE UI TEST' in ui
    put('system/bin/leo-diag-ui',ui)
    put('leo-autostart-android',b'Native display/keypad diagnostic.\n',0o100644)
    put('prop.default',e['default.prop'][1],0o100644)
    ui_rc=e['init.qcom.rc'][1]+b'''
service leo_ui /system/bin/leo-diag-ui /dev/graphics/fb0 /dev/input/event1
    class main
    user root
    group root graphics input
    seclabel u:r:su:s0
    disabled
    oneshot

on boot
    start leo_ui
'''
    put('init.qcom.rc',ui_rc,0o100750)
if args.framework:
    put('sbin/leo-framework-mount',(out/'leo-framework-mount').read_bytes())
    put('sbin/leo-framework-exec',(out/'leo-framework-exec').read_bytes())
    put('leo-framework-enabled',b'Dedicated external SD image files only.\n',0o100644)
    put('leo-autostart-android',b'Android framework SD diagnostic.\n',0o100644)
    put('prop.default',e['default.prop'][1],0o100644)
    put('fstab.qcom',(here/'device/nokia/leo/framework.fstab').read_bytes(),0o100644)
    if args.internal:
        put('leo-framework-internal',b'Validated internal Android partitions.\n',0o100644)
        put('fstab.qcom',(here/'device/nokia/leo/internal.fstab').read_bytes(),0o100644)
    put('init.qcom.rc',b'''# System and test data are already mounted by bootstrap from SD files.
on init
    setprop ro.opengles.version 131072

on post-fs
    setprop ro.crypto.state unencrypted
    setprop ro.crypto.type none

on post-fs-data
    mkdir /data/local 0751 root root
    mkdir /data/local/tmp 0771 shell shell

on boot
    write /tmp/leo-stage android-framework-boot-action
    setprop service.bootanim.exit 1
    trigger nonencrypted
''',0o100750)
    if args.internal:
        internal_rc=e['init.qcom.rc'][1]+b'''
on post-fs-data
    # Volatile compressed RAM; no swap writes to internal flash or SD.
    write /sys/block/zram0/disksize 268435456
    exec -- /system/bin/mkswap /dev/block/zram0
    exec -- /system/bin/swapon /dev/block/zram0

on property:sys.boot_completed=1
    start leo_first_setup

service leo_first_setup /system/bin/sh /system/etc/leo-first-setup.sh
    user root
    group root system shell
    seclabel u:r:init:s0
    disabled
    oneshot
'''
        put('init.qcom.rc',internal_rc,0o100750)
if args.graphics:
    archive=(out/'graphics-runtime.tar.gz').read_bytes()
    graphics_meta=json.loads((out/'graphics-runtime.json').read_text())
    assert digest(archive)==graphics_meta['sha256']
    put('graphics-runtime.tar.gz',archive,0o100644)
    put('system/bin/leo-graphics-exec',(out/'leo-graphics-exec').read_bytes())
    put('system/bin/leo-graphics-prepare',b'''#!/system/bin/sh
/system/bin/busybox mkdir -p /tmp/leo-graphics
/system/bin/busybox tar -xzf /graphics-runtime.tar.gz -C /tmp/leo-graphics || exit 1
/system/bin/leo-graphics-exec /tmp/leo-graphics /system/bin/setprop leo.graphics.ready 1
''')
    graphics_rc=e['init.qcom.rc'][1].replace(b'    start leo_ui\n',b'    start leo_graphics_prepare\n')
    for service,program in (
        ('leo_graphics_prepare','/system/bin/sh /system/bin/leo-graphics-prepare'),
        ('leo_logd','/system/bin/leo-graphics-exec /tmp/leo-graphics /system/bin/logd'),
        ('leo_binder','/system/bin/leo-graphics-exec /tmp/leo-graphics /system/bin/servicemanager'),
        ('leo_hwbinder','/system/bin/leo-graphics-exec /tmp/leo-graphics /system/bin/hwservicemanager'),
        ('leo_composer','/system/bin/leo-graphics-exec /tmp/leo-graphics /vendor/bin/hw/android.hardware.graphics.composer@2.1-service'),
        ('leo_sf','/system/bin/leo-graphics-exec /tmp/leo-graphics /system/bin/surfaceflinger'),
        ('leo_scene','/system/bin/leo-graphics-exec /tmp/leo-graphics /system/bin/leo_surface_test')):
        graphics_rc+=f'\nservice {service} {program}\n    class main\n    user root\n    group root graphics input\n    seclabel u:r:su:s0\n    disabled\n    oneshot\n'.encode()
    graphics_rc+=b'''
on property:leo.graphics.ready=1
    start leo_logd
    start leo_binder
    start leo_hwbinder

on property:hwservicemanager.ready=true && property:leo.graphics.ready=1
    start leo_composer

on property:init.svc.leo_composer=running
    start leo_sf

on property:init.svc.leo_sf=running
    start leo_scene

on property:init.svc.leo_scene=stopped && property:leo.graphics.ready=1
    stop leo_sf
    stop leo_composer
    start leo_ui
'''
    put('init.qcom.rc',graphics_rc,0o100750)
new=bytearray()
for idx,(n,(f,b)) in enumerate(list(e.items())+[('TRAILER!!!',([0,0,0,0,1,0,0,0,0,0,0,0,0],b''))],1):
    f=f.copy();f[0]=idx;f[6]=len(b);f[11]=len(n.encode())+1
    new.extend(b'070701'+b''.join(f'{v:08x}'.encode() for v in f)+n.encode()+b'\0')
    new.extend(bytes((-len(new))%4));new.extend(b);new.extend(bytes((-len(new))%4))
new.extend(bytes((-len(new))%512));packed=gzip.compress(bytes(new),9,mtime=1)
up=lambda n:(n+page-1)//page*page
end=off+up(len(packed));assert end<=len(stock)
image=bytearray(stock);image[off:max(end,off+up(r))]=bytes(max(end,off+up(r))-off)
image[off:off+len(packed)]=packed;struct.pack_into('<I',image,16,len(packed))
assert image[64:576]==stock[64:576] # Keep stock command line byte-for-byte.
h=hashlib.sha1()
for b in (stock[page:page+k],packed,b''):h.update(b);h.update(struct.pack('<I',len(b)))
image[576:596]=h.digest();image=bytes(image)
assert image[page:page+k]==stock[page:page+k]
assert image[40:44]==stock[40:44] # Keep stock OS version field.
assert image[max(end,off+up(r)):]==stock[max(end,off+up(r)):]
check=parse(image)[4]
assert len(check)==len(e)
for n,(f,b) in e.items():assert check[n][1]==b and check[n][0][1]==f[1],n
stem='boot-android81-internal' if args.internal else 'recovery-android81-framework' if args.framework else 'recovery-android81-graphics' if args.graphics else 'recovery-android81-ui' if args.ui else 'recovery-init-diagnostic'
(out/(stem+'.img')).write_bytes(image)
report={'bytes':len(image),'md5':hashlib.md5(image).hexdigest(),'sha256':digest(image),
        'kernel_unchanged':True,'stock_os_version_preserved':True,'stock_cmdline_preserved':True,
        'aosp_recovery_mode_marker':True,'first_stage_dt_mount_skipped_as_recovery':True,
        'keyboard_boot_marker':'two flashes at brightness 40','bootstrap_sha256':digest(bootstrap),
        'android_init_sha256':digest(init),'android_init_hold_on_panic':True,
        'early_adbd_source':'AOSP 8.1 eng; root controlled by LEO_EARLY_ADB environment',
        'early_adbd_sha256':digest(early_adbd),
        'early_adb_private_mount_namespace':True,'gate_file':'/tmp/leo-run-android',
        'persistent_partitions_mounted':['cache (validated mmcblk0p32, 409600 sectors; diagnostic logs only)'],
        'cache_logs':['/cache/leo-init-v4-bootstrap.log','/cache/leo-init-v4-adbd.log','/cache/leo-init-v4-kernel.log'],
        'auto_normal_reboot_without_init_gate':True,
        'selinux_mode_requested':'permissive via diagnostic init environment',
        'status':'offline verified; hardware pending'}
report.update(native_ui_embedded=args.ui,android_init_autostart=args.ui,framework_launcher_included=False)
report.update(graphics_embedded=args.graphics,ramdisk_compressed_bytes=len(packed),image_payload_end=end)
if args.framework:
    framework_meta=json.loads((out/'framework-runtime.json').read_text())
    report.update(framework_launcher_included=True,framework_boot_pending=True,android_init_autostart=True,auto_normal_reboot_without_init_gate=False,external_test_image_mounts=['system read-only','data read-write'],framework_system_image_sha256=framework_meta['images']['system.raw.img']['sha256'])
    if args.internal:report.update(boot_from_internal_partitions=True,external_test_image_mounts=[],persistent_partitions_mounted=['system read-only','userdata read-write','cache diagnostic logs'],stock_image_base='boot',recovery_partition_unchanged=True)
if args.graphics:report['graphics_runtime_sha256']=graphics_meta['sha256']
if args.ui:
    report.update(auto_normal_reboot_without_init_gate=False,native_ui_sha256=digest(ui))
(out/(stem+'.json')).write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
