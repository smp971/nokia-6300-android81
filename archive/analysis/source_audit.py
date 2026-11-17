"""Read-only firmware audit and small, attributable public-source snapshots."""
from pathlib import Path
import concurrent.futures
from datetime import datetime, timezone
import hashlib
import json
import re
import struct
import urllib.request
import zlib

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'analysis' / 'source-audit'
OUT.mkdir(exist_ok=True)
PIN = '949a9f7b2c109f307e72b25fec2dc5cb4d119dec'
BASE = f'https://raw.githubusercontent.com/bmndc/nokia-leo/{PIN}/'
sources = {
    'stock-kernel-Makefile': BASE + 'kernel/msm-4.9/Makefile',
    'stock-LEO_defconfig': BASE + 'kernel/msm-4.9/arch/arm/configs/LEO_defconfig',
    'stock-gecko-milestone.txt': BASE + 'gecko/config/milestone.txt',
    'stock-LEO.dts': BASE + 'kernel/msm-4.9/arch/arm/boot/dts/qcom/LEO.dts',
    'stock-LEO.dtsi': BASE + 'kernel/msm-4.9/arch/arm/boot/dts/qcom/LEO.dtsi',
    'stock-LEO-perf_defconfig': BASE + 'kernel/msm-4.9/arch/arm/configs/LEO-perf_defconfig',
    'modern-gecko-gonk.md': 'https://raw.githubusercontent.com/kaiostech/gecko-b2g/gonk/gonk.md',
    'modern-gecko-milestone.txt': 'https://raw.githubusercontent.com/kaiostech/gecko-b2g/gonk/config/milestone.txt',
    'pmos-deviceinfo': 'https://gitlab.com/postmarketOS/pmaports/-/raw/master/device/testing/device-nokia-leo/deviceinfo',
    'pmos-APKBUILD': 'https://gitlab.com/postmarketOS/pmaports/-/raw/master/device/testing/device-nokia-leo/APKBUILD',
    'pmos-panel.txt': 'https://gitlab.com/postmarketOS/pmaports/-/raw/master/device/testing/device-nokia-leo/nokia,leo-gc9305-hlt-24-panel.txt',
    'pmos-modules-initfs': 'https://gitlab.com/postmarketOS/pmaports/-/raw/master/device/testing/device-nokia-leo/modules-initfs',
    'mainline-leo.dts': 'https://raw.githubusercontent.com/msm8916-mainline/linux/wip/msm8916/7.0/arch/arm/boot/dts/qcom/qcom-msm8909-nokia-leo.dts',
    'mainline-leo-common.dtsi': 'https://raw.githubusercontent.com/msm8916-mainline/linux/wip/msm8916/7.0/arch/arm/boot/dts/qcom/qcom-msm8909-nokia-leo-common.dtsi',
    'pmos-current-APKBUILD': 'https://gitlab.postmarketos.org/postmarketOS/pmaports/-/raw/master/device/testing/device-nokia-leo/APKBUILD',
    'android81-reference-BoardConfig.mk': 'https://raw.githubusercontent.com/Huawei-Y6/android_device_huawei_scale/lineage-15.1/BoardConfig.mk',
    'android81-reference-common.mk': 'https://raw.githubusercontent.com/Huawei-Y6/android_device_huawei_msm8909-common/lineage-15.1/BoardConfigCommon.mk',
}

def fetch(item):
    name, url = item
    record = {'file': name, 'url': url, 'retrieved_utc': datetime.now(timezone.utc).isoformat()}
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'nokia-leo-source-audit'})
        with urllib.request.urlopen(req, timeout=35) as response:
            data = response.read()
            record['resolved_url'] = response.url
        if data.lstrip().lower().startswith((b'<!doctype html', b'<html')):
            raise ValueError('HTML received instead of source; not accepted')
        (OUT / name).write_bytes(data)
        record.update(sha256=hashlib.sha256(data).hexdigest(), size=len(data), status='downloaded')
    except Exception as exc:
        record.update(status='unavailable', error=str(exc))
    return record

with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
    records = list(pool.map(fetch, sources.items()))
(OUT / 'sources.json').write_text(json.dumps(records, indent=2), encoding='utf-8')

boot = (ROOT / 'partitions/boot.bin').read_bytes()
assert boot[:8] == b'ANDROID!'
ksz, ka, rsz, ra, ssz, sa, tags, page = struct.unpack_from('<8I', boot, 8)
kernel = boot[page:page+ksz]
payload = kernel
for match in re.finditer(b'\x1f\x8b\x08', kernel):
    try:
        candidate = zlib.decompress(kernel[match.start():], 16+zlib.MAX_WBITS)
        if b'Linux version ' in candidate:
            payload = candidate
            break
    except zlib.error:
        continue
versions = re.findall(rb'Linux version [^\x00\r\n]{1,220}', payload)
config = None
offset = payload.find(b'IKCFG_ST')
if offset >= 0:
    try:
        config = zlib.decompress(payload[offset+8:], 16+zlib.MAX_WBITS).decode()
        (OUT / 'dump-kernel.config').write_text(config, encoding='utf-8')
    except zlib.error:
        pass
props = {}
for line in (ROOT / 'analysis/system/build.prop').read_text().splitlines():
    if line and not line.startswith('#') and '=' in line:
        k,v = line.split('=',1)
        props[k]=v
inventory = {
    'boot_sha256': hashlib.sha256(boot).hexdigest(),
    'boot_header': dict(kernel_size=ksz, kernel_addr=hex(ka), ramdisk_size=rsz,
                        ramdisk_addr=hex(ra), second_size=ssz, tags_addr=hex(tags), pagesize=page),
    'kernel_version_strings': [v.decode(errors='replace') for v in versions],
    'embedded_config_extracted': config is not None,
    'properties': {k:v for k,v in props.items() if k in (
        'ro.build.version.base_os','ro.build.version.release','ro.build.version.sdk',
        'ro.build.version.incremental','ro.product.cpu.abilist','ro.board.platform','ro.config.low_ram')},
    'android_framework_presence': {p:(ROOT/'analysis/system'/p).exists() for p in (
        'bin/app_process','bin/app_process32','bin/surfaceflinger','framework/framework.jar',
        'lib/libart.so','bin/servicemanager','bin/rild')},
    'hardware_modules': sorted(p.name for p in (ROOT/'analysis/system/lib/hw').glob('*.so')),
}
(OUT / 'dump-inventory.json').write_text(json.dumps(inventory,indent=2),encoding='utf-8')
print(json.dumps({'sources': records, 'dump':inventory},indent=2))
