"""Verify automatic Vodafone LTE data after boot on the pinned Nokia."""
from pathlib import Path
import argparse,importlib.util,json,re,subprocess,time
root=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('radio_installer',root/'install-radio-runtime.py')
radio=importlib.util.module_from_spec(spec);spec.loader.exec_module(radio)
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--wait',action='store_true');args=p.parse_args()
deadline=time.monotonic()+(210 if args.wait else 1);previous=None
while True:
    try:
        assert 'androidboot.serialno=695d' in radio.shell('/system/bin/cat','/proc/cmdline')
        service=radio.shell('/system/bin/getprop','init.svc.leo_netmgrd')
        technology=radio.shell('/system/bin/getprop','gsm.network.type')
        summary=(service,technology)
        if summary!=previous:print('netmgr, radio technologies:',summary,flush=True);previous=summary
        connectivity=radio.shell('/system/bin/dumpsys','connectivity')
        mobile=next((line for line in connectivity.splitlines() if 'NetworkAgentInfo{' in line and 'MOBILE[LTE]' in line and 'CONNECTED/CONNECTED' in line),'')
        if service=='running' and 'VALIDATED' in mobile:
            break
    except (RuntimeError,TimeoutError,subprocess.TimeoutExpired):pass
    if time.monotonic()>deadline:raise SystemExit('Automatic validated LTE data not observed; inspect private diagnostics.')
    time.sleep(4)
iface=re.search(r'InterfaceName: (\S+)',mobile).group(1)
assert re.fullmatch(r'rmnet_data\d+',iface)
ping=radio.shell('/system/bin/ping','-I',iface,'-c','2','-W','3','1.1.1.1')
assert '2 received' in ping
assert ' /system ext4 ro,' in radio.shell('/system/bin/cat','/proc/1/mounts')
assert radio.shell('/system/bin/getprop','gsm.sim.state')=='READY,READY'
registry=radio.shell('/system/bin/dumpsys','telephony.registry')
assert len(re.findall(r'mVoiceRegState=0\(IN_SERVICE\)',registry))>=2
report={'boot_id':radio.shell('/system/bin/cat','/proc/sys/kernel/random/boot_id'),'netmgr_running':True,'lte_data_validated':True,'interface':iface,'ping_sent':2,'ping_received':2,'both_sims_ready':True,'both_voice_slots_registered':True,'system_read_only':True,'volte_vowifi_tested':False}
(root/'output/lte-investigation/lte-runtime-verified.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2),flush=True)
