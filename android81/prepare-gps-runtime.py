"""Stage an isolated GNSS trial, without changing system files or enabling GPS."""
from pathlib import Path
import importlib.util,subprocess
root=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('radio',root/'install-radio-runtime.py');r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r)
base='/tmp/leo-gnss-runtime'
r.shell('/system/bin/mkdir','-p',base+'/hw',base+'/lib',base+'/etc')
r.shell('/system/bin/cp','-a','/vendor/lib/hw/.',base+'/hw/')
r.shell('/system/bin/cp','-a','/system/etc/.',base+'/etc/')
product=Path('//wsl.localhost/kaios/root/android81-leo/src/out/target/product/leo/system')
files={'exec':root/'output/leo-gps-runtime-exec','gnss-provider':product/'vendor/bin/hw/android.hardware.gnss@1.0-service','hw/android.hardware.gnss@1.0-impl.so':product/'vendor/lib/hw/android.hardware.gnss@1.0-impl.so','hw/gps.default.so':root.parent/'analysis/system/lib/hw/gps.default.so','etc/gps.conf':root.parent/'analysis/system/etc/gps.conf'}
for name in ['libloc_eng.so','libloc_core.so','libloc_api_v02.so','libloc_ds_api.so','libgps.utils.so']:files['lib/'+name]=root.parent/'analysis/system/lib'/name
for name,path in files.items():subprocess.run(r.ADB+['push',str(path),base+'/'+name],check=True,stdout=subprocess.DEVNULL);r.shell('/system/bin/chmod','755' if name in ('exec','gnss-provider') else '644',base+'/'+name)
r.shell('/system/bin/mkdir','-p','/data/misc/location','/data/gps')
r.shell('/system/bin/chown','gps:system','/data/misc/location','/data/gps')
r.shell('/system/bin/chmod','770','/data/misc/location','/data/gps')
print('Staged GNSS service and private stock HAL. No service started.')
