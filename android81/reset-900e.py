"""Reset only the observed Qualcomm crash-mode port; never uploads a programmer.
Protocol reference: https://github.com/linux-msm/qdl/blob/master/src/sahara.c
"""
import argparse,struct,time,subprocess
import serial
from serial.tools import list_ports
p=argparse.ArgumentParser();p.add_argument('--port',default='COM32');args=p.parse_args()
ports=[x for x in list_ports.comports() if x.device.upper()==args.port.upper() and x.vid==0x05c6 and x.pid==0x900e]
assert len(ports)==1,'The requested port is not the observed Qualcomm 900E device.'
print('Resetting crash-mode port '+args.port,flush=True)
try:
 with serial.Serial(args.port,115200,timeout=1,write_timeout=2) as port:
  port.write(struct.pack('<12I',2,48,2,1,0,2,0,0,0,0,0,0));port.flush()
  reply=port.read(256);print('Handshake:',reply[:16].hex(' '),flush=True)
  for _ in range(3):
   port.write(struct.pack('<2I',7,8));port.flush()
   reply=port.read(256)
   if len(reply)>=8 and struct.unpack_from('<I',reply)[0]==8:
    print('Reset acknowledged.',flush=True);break
except serial.SerialException as exc:
 print('Serial transport ended: '+str(exc),flush=True)
# A disappearing COM port alone does not establish that Android booted.
for _ in range(15):
 r=subprocess.run(['D:/ROMS/platform-tools/adb.exe','-s','695d','get-state'],capture_output=True,text=True,timeout=3)
 if r.returncode==0 and r.stdout.strip()=='device':
  print('Nokia serial 695d returned over ADB.');break
 time.sleep(1)
else:raise SystemExit('ADB has not returned; no successful Android reboot claimed.')
