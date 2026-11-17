"""Prepare measured port inputs. No flashing, remounting or device writes."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import struct
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'android81'
DEVICE = OUT / 'device/nokia/leo'
parser = argparse.ArgumentParser()
parser.add_argument('--adb', type=Path)
parser.add_argument('--serial', help='Explicit target; required with --adb')
parser.add_argument('--backup-boot', action='store_true', help='Read boot/recovery into local backup files')
args = parser.parse_args()
if args.adb and not args.serial:
    parser.error('--serial is required with --adb')
DEVICE.mkdir(parents=True, exist_ok=True)
(DEVICE/'prebuilt').mkdir(exist_ok=True)
(DEVICE/'reference-stock').mkdir(exist_ok=True)
boot = (ROOT/'partitions/boot.bin').read_bytes()
assert boot[:8] == b'ANDROID!'
ksz,ka,rsz,ra,ssz,sa,tags,page = struct.unpack_from('<8I',boot,8)
kernel = boot[page:page+ksz]
(DEVICE/'prebuilt/kernel').write_bytes(kernel)
cmdline = boot[64:576].split(b'\0')[0].decode() + boot[608:1632].split(b'\0')[0].decode()
parts = {}
for p in ET.parse(ROOT/'meta/qfil_rawprogram0.xml').getroot().findall('program'):
    if p.get('label') in ('boot','recovery','system','userdata','cache'):
        parts[p.get('label')] = int(p.get('num_partition_sectors')) * int(p.get('SECTOR_SIZE_IN_BYTES'))
for name in ('fstab.qcom','init.qcom.rc','init.qcom.usb.rc','ueventd.qcom.rc'):
    shutil.copyfile(ROOT/'analysis/boot/rd'/name, DEVICE/'reference-stock'/name)
shutil.copyfile(ROOT/'analysis/system/usr/keylayout/matrix_keypad.kl',DEVICE/'reference-stock/matrix_keypad.kl')
shutil.copyfile(ROOT/'analysis/source-audit/dump-kernel.config',DEVICE/'reference-stock/kernel.config')
facts = {'source_boot_sha256':hashlib.sha256(boot).hexdigest(),
         'kernel_sha256':hashlib.sha256(kernel).hexdigest(),
         'kernel_size':ksz, 'pagesize':page, 'stock_cmdline':cmdline,
         'addresses':dict(kernel=hex(ka),ramdisk=hex(ra),tags=hex(tags)),
         'partition_bytes_from_public_dump_xml':parts,
         'status':'preparation only; no successful Android build or boot'}
(OUT/'port-inputs.json').write_text(json.dumps(facts,indent=2),encoding='utf-8')
board = f'''# EXPERIMENTAL: measured inputs, not a complete or tested device tree.
# Partition sizes are from the PUBLIC dump; verify against the actual target.
TARGET_ARCH := arm
TARGET_ARCH_VARIANT := armv7-a-neon
TARGET_CPU_VARIANT := cortex-a7
TARGET_CPU_ABI := armeabi-v7a
TARGET_CPU_ABI2 := armeabi
TARGET_BOARD_PLATFORM := msm8909
TARGET_BOOTLOADER_BOARD_NAME := msm8909
TARGET_NO_BOOTLOADER := true
TARGET_PREBUILT_KERNEL := device/nokia/leo/prebuilt/kernel
BOARD_KERNEL_BASE := 0x80000000
BOARD_KERNEL_PAGESIZE := {page}
BOARD_KERNEL_OFFSET := 0x{ka-0x80000000:08x}
BOARD_RAMDISK_OFFSET := 0x{ra-0x80000000:08x}
BOARD_TAGS_OFFSET := 0x{tags-0x80000000:08x}
BOARD_KERNEL_CMDLINE := {cmdline}
BOARD_MKBOOTIMG_ARGS += --ramdisk_offset $(BOARD_RAMDISK_OFFSET)
BOARD_MKBOOTIMG_ARGS += --tags_offset $(BOARD_TAGS_OFFSET)
TARGET_USERIMAGES_USE_EXT4 := true
BOARD_SYSTEMIMAGE_PARTITION_SIZE := {parts['system']}
BOARD_BOOTIMAGE_PARTITION_SIZE := {parts['boot']}
BOARD_RECOVERYIMAGE_PARTITION_SIZE := {parts['recovery']}
BOARD_CACHEIMAGE_PARTITION_SIZE := {parts['cache']}
BOARD_CACHEIMAGE_FILE_SYSTEM_TYPE := ext4
# Userdata size/encryption policy deliberately require target validation.
# TODO: product definition, Android 8.1 init/USB, fstab, SELinux, VINTF,
# Binder ABI, graphics HAL linkage, proprietary dependencies and key mapping.
'''
board_path = DEVICE/'BoardConfig.mk'
if not board_path.exists():
    board_path.write_text(board,encoding='utf-8')
else:
    print('Keeping existing BoardConfig.mk (may contain manual port changes).')
if args.adb:
    def adb(*command):
        r = subprocess.run([str(args.adb),'-s',args.serial,*command],capture_output=True,text=True,timeout=30)
        if r.returncode:
            raise RuntimeError(r.stderr.strip() or r.stdout.strip())
        return r.stdout.strip()
    model=adb('shell','getprop ro.product.model')
    if model != 'Nokia 6300 4G':
        raise RuntimeError(f'Unexpected target model: {model!r}')
    queries = {
        'identity':'id',
        'kernel':'uname -a',
        'memory':'cat /proc/meminfo',
        'framebuffer':'cat /sys/class/graphics/fb0/name; cat /sys/class/graphics/fb0/virtual_size; cat /sys/class/graphics/fb0/bits_per_pixel',
        'input_devices':'cat /proc/bus/input/devices',
        'partition_links':'ls -l /dev/block/bootdevice/by-name',
        'mounts':'cat /proc/mounts',
        'usb_config':'getprop sys.usb.config',
        'build':'getprop ro.build.version.incremental',
        'panel_bootarg':'cat /proc/cmdline',
        'panel_sysfs':'ls /sys/class/graphics/fb0',
        'panel_info':'cat /sys/class/graphics/fb0/msm_fb_panel_info',
        'partition_sizes':'for p in boot recovery system userdata cache; do echo $p; blockdev --getsize64 /dev/block/bootdevice/by-name/$p; done',
    }
    live = {'model':model,'read_only':True,'results':{}}
    for name,cmd in queries.items():
        try:
            live['results'][name]={'output':adb('shell',cmd),'status':'ok'}
        except Exception as exc:
            live['results'][name]={'error':str(exc),'status':'failed'}
    (OUT/'live-device.json').write_text(json.dumps(live,indent=2),encoding='utf-8')
    if args.backup_boot:
        backup = OUT/'backup-live'
        backup.mkdir(exist_ok=True)
        checksums={}
        for name in ('boot','recovery'):
            target=backup/(name+'-device.img')
            if target.exists():
                raise RuntimeError(f'Refusing to replace existing backup: {target}')
            remote=adb('shell','readlink -f /dev/block/bootdevice/by-name/'+name)
            expected=int(adb('shell','blockdev --getsize64 '+remote))
            r=subprocess.run([str(args.adb),'-s',args.serial,'pull',remote,str(target)],capture_output=True,timeout=180)
            if r.returncode or target.stat().st_size != expected:
                raise RuntimeError(f'Backup failed or truncated: {target}')
            checksums[name]={'bytes':expected,'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),
                             'md5':hashlib.md5(target.read_bytes()).hexdigest(),
                             'device_md5':adb('shell','md5sum '+remote).split()[0]}
            if checksums[name]['md5'] != checksums[name]['device_md5']:
                raise RuntimeError(f'Backup checksum mismatch: {name}')
        (backup/'checksums.json').write_text(json.dumps(checksums,indent=2),encoding='utf-8')
    print('Read-only target inventory collected:',model)
print('Prepared:',OUT)
print('No Android image was built or flashed.')
