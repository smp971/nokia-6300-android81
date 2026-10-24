"""Verify live selected-SIM IMS registration and LTE data without placing a call."""
from pathlib import Path
import argparse, importlib.util, json, re, time
root=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('radio',root/'install-radio-runtime.py')
radio=importlib.util.module_from_spec(spec);spec.loader.exec_module(radio)
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--wait',action='store_true')
args=parser.parse_args()
deadline=time.monotonic()+(180 if args.wait else 0)
last=None
while True:
    properties=dict(re.findall(r'^\[([^]]+)\]: \[(.*)\]$',radio.shell('/system/bin/getprop').replace('\r',''),re.M))
    keys=['sys.boot_completed','persist.leo.ims.enabled','sys.leo.ims.state',
          'sys.leo.ims.slot','sys.leo.ims.client_slot','sys.leo.ims.native_ready',
          'sys.leo.ims.registration','sys.leo.ims.voice']
    keys+=['init.svc.leo_ims_'+name for name in ('watchdog','qmi','data','sockets')]
    report={key:properties.get(key,'') for key in keys}
    if report!=last:print(json.dumps(report),flush=True);last=report
    ready=(report['sys.boot_completed']=='1' and report['persist.leo.ims.enabled']=='1'
           and report['sys.leo.ims.state']=='registered'
           and report['sys.leo.ims.registration']=='1' and report['sys.leo.ims.voice']=='1'
           and report['sys.leo.ims.native_ready']=='1'
           and report['sys.leo.ims.slot']==report['sys.leo.ims.client_slot']
           and all(report['init.svc.leo_ims_'+name]=='running' for name in ('watchdog','qmi','data','sockets')))
    if ready:
        controller=radio.shell('/system/bin/sh','-c','pidof org.codeaurora.ims:leo_controller || true')
        transport=radio.shell('/system/bin/sh','-c','pidof org.codeaurora.ims:leo_ims || true')
        slot=int(report['sys.leo.ims.slot']);assert slot in (0,1)
        registry=radio.shell('/system/bin/dumpsys','telephony.registry').split('local logs:')[0]
        calls=re.findall(r'^\s*mCallState=(\d+)',registry,re.M)
        data=re.findall(r'^\s*mDataConnectionState=(-?\d+)',registry,re.M)
        rat=re.findall(r'mRilDataRadioTechnology=([^,}]+)',registry)
        dump=radio.shell('/system/bin/dumpsys','activity','service','org.codeaurora.ims/.ImsService')
        live='LEO_IMS slot=%d phone=%d registration=1 voice_available=true'%(slot,slot) in dump
        connected=radio.shell('/system/bin/dumpsys','connectivity')
        validated=any('NetworkAgentInfo' in line and 'MOBILE[LTE]' in line and 'VALIDATED' in line for line in connected.splitlines())
        report.update(call_states=calls,data_states=data,data_rats=rat,client_live=live,controller_live=bool(controller),transport_live=bool(transport),mobile_lte_validated=validated)
        if live and controller and transport and len(data)==2 and data[slot]=='2' and rat[slot]=='14(LTE)' and validated:
            (root/'output/ims-runtime-verified.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
            print('PASS: live selected-SIM IMS voice registration and validated LTE data.',flush=True)
            break
    if time.monotonic()>=deadline:raise SystemExit('IMS/LTE runtime is not fully ready; inspect the reported state.')
    time.sleep(5)
