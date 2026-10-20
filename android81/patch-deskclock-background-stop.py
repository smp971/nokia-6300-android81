"""Patch the stock Oreo clock STOP_ALARM startup; export its reproducible AOSP diff."""
from pathlib import Path
import hashlib,json,subprocess,xml.etree.ElementTree as E
root=Path(__file__).resolve().parent
src=Path('/root/android81-leo/src/packages/apps/DeskClock')
target=src/'src/com/android/deskclock/alarms/AlarmService.java'
projects=E.parse(root/'repro/aosp-manifest.xml').getroot().findall('project')
revision=next(x.get('revision') for x in projects if x.get('path')=='packages/apps/DeskClock')
assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=src,text=True).strip()==revision
java_path='src/com/android/deskclock/alarms/AlarmService.java'
manifest=src/'AndroidManifest.xml'
base=subprocess.check_output(['git','show','HEAD:'+java_path],cwd=src,text=True)
current=target.read_text()
assert current==base or 'Oreo also restricts STOP requests' in current or 'class StopReceiver' in current,'Unexpected AlarmService changes'
a=base.index('    public static void stopAlarm(')
b=base.index('    private TelephonyManager',a)
replacement="""    private static AlarmService sRunningService;

    // Explicit, non-exported receiver: a STOP must never start a background service.
    public static final class StopReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!STOP_ALARM_ACTION.equals(intent.getAction())) return;
            final AlarmService service = sRunningService;
            if (service == null) return;
            final long instanceId = AlarmInstance.getId(intent.getData());
            if (service.mCurrentAlarm != null
                    && service.mCurrentAlarm.mId != instanceId) return;
            service.stopCurrentAlarm();
            service.stopSelf();
        }
    }

    public static void stopAlarm(Context context, AlarmInstance instance) {
        final Intent intent = AlarmInstance.createIntent(context, StopReceiver.class, instance.mId)
                .setAction(STOP_ALARM_ACTION);
        context.sendBroadcast(intent);
    }

"""
s=base[:a]+replacement+base[b:]
s=s.replace('        super.onCreate();','        super.onCreate();\n        sRunningService = this;',1)
s=s.replace('        super.onDestroy();','        super.onDestroy();\n        if (sRunningService == this) sRunningService = null;',1)
target.write_text(s)
m_base=subprocess.check_output(['git','show','HEAD:AndroidManifest.xml'],cwd=src,text=True)
m_current=manifest.read_text()
assert m_current==m_base or '.alarms.AlarmService$StopReceiver' in m_current,'Unexpected manifest changes'
needle='        <service\n            android:name=".alarms.AlarmService"'
assert needle in m_base
manifest.write_text(m_base.replace(needle,'        <receiver\n            android:name=".alarms.AlarmService$StopReceiver"\n            android:exported="false"\n            android:directBootAware="true" />\n\n'+needle,1))
patch=subprocess.check_output(['git','diff','--full-index','HEAD','--',java_path,'AndroidManifest.xml'],cwd=src)
assert patch and b'PRIVATE KEY' not in patch
out=root/'repro/patches/packages__apps__DeskClock.patch';out.write_bytes(patch)
index=root/'repro/sources.json';data=json.loads(index.read_text());data['projects']=[x for x in data['projects'] if x['path']!='packages/apps/DeskClock'];data['projects'].append({'path':'packages/apps/DeskClock','revision':revision,'files':[],'patch':'patches/packages__apps__DeskClock.patch','sha256':hashlib.sha256(patch).hexdigest()});index.write_text(json.dumps(data,indent=2)+'\n')
print('DeskClock stop-alarm patch exported; all other AOSP project records retained')
