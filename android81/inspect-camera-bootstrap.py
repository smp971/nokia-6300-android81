from pathlib import Path
import json,re
root=Path(__file__).resolve().parent
stock=root.parent/'analysis/system'
files={'daemon':stock/'bin/mm-qcamera-daemon','sensor_modules':stock/'vendor/lib/libmmcamera2_sensor_modules.so'}
markers=['module_sensor_init','server_process_module_sensor_init','sensor_init_probe','sensor_init_session']
rows={}
for key,path in files.items():
 data=path.read_bytes()
 strings={s.decode('ascii') for s in re.findall(rb'[ -~]{6,}',data)}
 rows[key]={'file':path.name,'bootstrap_markers':[m for m in markers if any(m in s for s in strings)]}
init=root.parent/'analysis/boot/rd/init.target.rc'
text=init.read_text(encoding='utf-8',errors='replace') if init.exists() else ''
block=re.search(r'(?m)^service qcamerasvr[^\n]*\n(?:[ \t].*(?:\n|$))*',text)
report={'offline_only':True,'files':rows,'stock_service':block.group(0).strip() if block else None,
 'vendor_mm_camera_libraries':len(list((stock/'vendor/lib').glob('libmmcamera*.so'))),
 'inference':'Review daemon -> module_sensor_init -> sensor_init_probe before retrying the HAL count call; exact ioctl handshake is not proven by strings.',
 'driver_or_daemon_started':False,'sensor_identified':False,'capture_tested':False}
(root/'output/camera-bootstrap-audit.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
