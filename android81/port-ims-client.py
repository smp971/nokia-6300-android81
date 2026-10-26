"""Apply the Android 8.1 compatibility port to a fresh, pinned local IMS study tree.

Scope: legacy socket transport, one explicitly selected SIM, MMTEL voice. No RTT, CSVT or
multi-endpoint implementation is advertised. Vendor source stays in ignored files.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import hashlib,json

root=Path('/root/android81-leo/src/vendor/leo-ims-candidate/src')
ims=root/'org/codeaurora/ims'
framework=Path('/root/android81-leo/src/frameworks/base/telephony/java/com/android/ims/internal')
runtime=Path(__file__).resolve().parent/'ims-runtime'
reference=Path(__file__).resolve().parent/'output/ims-reference/client'
manifest=json.loads((reference/'source-manifest.json').read_text())
assert manifest['revision']=='f242858a8cbb2c9ad9b5b2e53d75380ae775727a'
for name,digest in manifest['sha256'].items():
    assert hashlib.sha256((reference/name).read_bytes()).hexdigest()==digest,name

def body_end(s,start):
    depth=0
    for token in re.finditer(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"|\x27(?:\\.|[^\x27\\])*\x27|[{}]',s[start:],re.S):
        t=token.group()
        if t=='{':depth+=1
        elif t=='}':
            depth-=1
            if depth==0:return start+token.end()
    raise ValueError('Unbalanced Java body')

def method(s,name,replacement):
    m=re.search(r'(?:@Override\s*)?(?:public|private|protected)\s+(?:final\s+)?[\w<>\[\]]+\s+'+name+r'\s*\([^)]*\)\s*(?:throws[^{}]+)?\{',s)
    assert m,name
    return s[:m.start()]+replacement+s[body_end(s,s.index('{',m.start())):]

def append(s,code):
    at=s.rfind('}');return s[:at]+code+'\n'+s[at:]

def update(path,fn):path.write_text(fn(path.read_text()))

(ims/'LeoImsSlot.java').write_text('''package org.codeaurora.ims;
/** Fixed for the lifetime of a controlled trial; never remap an active call. */
final class LeoImsSlot {
    static int get() {
        int slot = android.os.SystemProperties.getInt("sys.leo.ims.slot", 0);
        if (slot < 0 || slot > 1) throw new IllegalArgumentException("Selected IMS slot must be 0 or 1");
        return slot;
    }
}
''')

# Public API moves/renames retain the underlying values and behavior.
for path in root.rglob('*.java'):
    s=path.read_text().replace('VideoProfile.VideoState.','VideoProfile.')
    for name in ('AUDIO_ONLY','RX_ENABLED','TX_ENABLED','BIDIRECTIONAL','PAUSED'):
        s=s.replace('VideoProfile.'+name,'VideoProfile.STATE_'+name)
    s=s.replace('import android.telecom.CameraCapabilities;','import android.telecom.VideoProfile.CameraCapabilities;')
    s=s.replace('CameraDevice.StateListener','CameraDevice.StateCallback').replace('CameraCaptureSession.StateListener','CameraCaptureSession.StateCallback')
    s=s.replace('ImsManager.EXTRA_PHONEID','ImsManager.EXTRA_PHONE_ID')
    s=s.replace('ImsCallProfile.CALL_SUBSTATE_NONE','0').replace('ImsCallProfile.EXTRA_CALL_DOMAIN','"CallDomain"')
    s=s.replace('Connection.CALL_SUBSTATE_NONE','0')
    s=s.replace('ImsServiceClass.CSVT','2 /* legacy CSVT class, not exposed by this port */')
    path.write_text(s)

# Binder entry points may construct sessions/UT objects on a thread without a Looper.
for filename,handler in [('ImsEcbmImpl.java','ImsEcbmImplHandler'),('ImsConfigImpl.java','ImsConfigImplHandler'),('ImsServiceSub.java','ImsServiceSubHandler'),('ImsUtImpl.java','ImsUtImplHandler'),('ImsCallSessionImpl.java','ImsCallSessionImplHandler'),('ImsCallModification.java','ImsCallModificationHandler')]:
    path=ims/filename;s=path.read_text()
    if re.search(r'\b'+handler+r'\s*\(\s*\)',s[s.index('class '+handler):]):
        s=s.replace('super();','super(android.os.Looper.getMainLooper());')
    else:
        needle='class '+handler+' extends Handler {'
        assert needle in s
        s=s.replace(needle,needle+'\n        '+handler+'() { super(android.os.Looper.getMainLooper()); }')
    path.write_text(s)

def service(s):
    s=re.sub(r'^import com.android.internal.telephony.ModemStackController[^;]*;\n','',s,flags=re.M)
    s=s.replace('private ModemStackController mModemStackController;','// Leo: one selected LTE slot, fixed for the process lifetime.')
    s=s.replace('mServiceSub = new ImsServiceSub[getNumSubscriptions()];','mImsPhoneId = LeoImsSlot.get();\n        mServiceSub = new ImsServiceSub[getNumSubscriptions()];')
    s=re.sub(r'^.*ImsVideoGlobals\.(?:init|getInstance)\([^\n]*\n','',s,flags=re.M)
    s=method(s,'initSubscriptionStatus','private void initSubscriptionStatus() { mServiceSub[0].registerForPhoneId(mImsPhoneId); }')
    # Dispose a stale Android IMS phone before publishing the new Binder/UP event.
    s=s.replace('        initSubscriptionStatus();','')
    s=s.replace('        ServiceManager.addService("ims", mBinder);','''        initSubscriptionStatus();
        android.os.SystemProperties.set("sys.leo.ims.client_slot", Integer.toString(mImsPhoneId));
        android.os.SystemProperties.set("sys.leo.ims.registration", "2");
        android.os.SystemProperties.set("sys.leo.ims.voice", "0");
        Intent down = new Intent(ImsManager.ACTION_IMS_SERVICE_DOWN);
        down.setPackage("com.android.phone");
        down.putExtra(ImsManager.EXTRA_PHONE_ID, mImsPhoneId);
        sendBroadcast(down);
        ServiceManager.addService("ims", mBinder);''')
    s=method(s,'onDestroy','''public void onDestroy() {
        Intent down = new Intent(ImsManager.ACTION_IMS_SERVICE_DOWN);
        down.setPackage("com.android.phone");
        down.putExtra(ImsManager.EXTRA_PHONE_ID, mImsPhoneId);
        sendBroadcast(down);
        android.os.SystemProperties.set("sys.leo.ims.client_slot", "-1");
        android.os.SystemProperties.set("sys.leo.ims.registration", "2");
        android.os.SystemProperties.set("sys.leo.ims.voice", "0");
        super.onDestroy();
        // Legacy transport/session singletons must not survive a physical-slot change.
        new Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            public void run() { android.os.Process.killProcess(android.os.Process.myPid()); }
        }, 100);
    }''')
    s=method(s,'handleModemStackReady','private void handleModemStackReady(Message msg) { throw new UnsupportedOperationException("Dynamic modem stack mapping is not supported"); }')
    needle='private final IImsService.Stub mBinder = new IImsService.Stub() {'
    assert needle in s
    s=s.replace(needle,needle+'''
        public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException {
            ImsService.this.enforceCallingOrSelfPermission("android.permission.MODIFY_PHONE_STATE", "IMS control requires phone permission");
            return super.onTransact(code, data, reply, flags);
        }
        public void addRegistrationListener(int phoneId, int serviceClass, IImsRegistrationListener listener) {
            if (phoneId != mImsPhoneId || serviceClass != ImsServiceClass.MMTEL) throw new IllegalArgumentException("Only selected MMTEL is supported");
            mServiceSub[0].addRegistrationListener(listener);
        }
        public com.android.ims.internal.IImsMultiEndpoint getMultiEndpointInterface(int serviceId) { return null; }
''')
    s=s.replace('return openForSub(1, serviceClass, incomingCallIntent, listener);','if (phoneId != mImsPhoneId || serviceClass != ImsServiceClass.MMTEL) return -1;\n           return openForSub(1, serviceClass, incomingCallIntent, listener);')
    s=s.replace('public IImsConfig getConfigInterface(int phoneId) {','public IImsConfig getConfigInterface(int phoneId) {\n            if (phoneId != mImsPhoneId) return null;')
    for name in ('turnOnIms','turnOffIms'):
        s=s.replace('public void '+name+'(int phoneId) {','public void '+name+'(int phoneId) {\n            if (phoneId != mImsPhoneId) return;')
    s=method(s,'isConnected','''public boolean isConnected(int serviceId, int serviceType, int callType) {
        ImsServiceSub service = mServiceSubMap.get(serviceId);
        return service != null && serviceType == ImsCallProfile.SERVICE_TYPE_NORMAL && callType == ImsCallProfile.CALL_TYPE_VOICE && service.isVoiceReadyForLeo();
    }''')
    s=method(s,'isOpened','public boolean isOpened(int serviceId) { return mServiceSubMap.containsKey(serviceId); }')
    s=method(s,'close','''public void close(int serviceId) {
        ImsServiceSub service = mServiceSubMap.remove(serviceId);
        if (service != null) service.closeForLeo(serviceId);
    }''')
    return append(s,'''public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra("leoReconfigure", false)) {
            if (LeoImsSlot.get() != mImsPhoneId) throw new IllegalStateException("Stop IMS before changing slot");
            ImsManager.updateImsServiceConfig(this, mImsPhoneId, true);
        }
        return START_NOT_STICKY;
    }
    protected void dump(java.io.FileDescriptor fd, java.io.PrintWriter out, String[] args) { mServiceSub[0].dumpForLeo(out); }
''')
update(ims/'ImsService.java',service)

# Generate a complete fanout against the actual 8.1 AIDL, including newer callbacks.
aidl=(framework/'IImsRegistrationListener.aidl').read_text()
aidl=re.sub(r'/\*.*?\*/|//[^\n]*','',aidl,flags=re.S)
fanout='''package org.codeaurora.ims;
import com.android.ims.internal.IImsRegistrationListener;
import com.android.ims.ImsReasonInfo;
import android.net.Uri;
public final class LeoRegistrationFanout extends IImsRegistrationListener.Stub {
 private final java.util.concurrent.CopyOnWriteArrayList<IImsRegistrationListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
 public void add(IImsRegistrationListener listener) {
  if(listener == null) return;
  for(IImsRegistrationListener old:listeners) if(old.asBinder().equals(listener.asBinder())) return;
  listeners.add(listener);
 }
 public void clear() { listeners.clear(); }
'''
for name,params in re.findall(r'void\s+(\w+)\s*\(([^)]*)\)\s*;',aidl):
    params=re.sub(r'\bin\s+','',params).strip()
    args=', '.join(x.strip().split()[-1] for x in params.split(',') if x.strip())
    fanout+=f' public void {name}({params}) {{ for(IImsRegistrationListener listener:listeners) {{ try {{ listener.{name}({args}); }} catch(android.os.RemoteException dead) {{ listeners.remove(listener); }} }} }}\n'
fanout+='}\n';(ims/'LeoRegistrationFanout.java').write_text(fanout)

def subscription(s):
    s=s.replace('import com.android.internal.telephony.PhoneBase;','import com.android.internal.telephony.Phone;').replace('import com.android.internal.telephony.PhoneProxy;','')
    s=method(s,'setPhoneId','''public void setPhoneId(int phoneId) {
        if (phoneId != LeoImsSlot.get()) throw new IllegalArgumentException("Only selected SIM supported");
        if (mPhoneId == phoneId) return;
        int[] subscriptions = android.telephony.SubscriptionManager.getSubId(phoneId);
        if (subscriptions == null || subscriptions.length == 0) throw new IllegalStateException("SIM not ready");
        mLeoTelephony = ((TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE)).createForSubscriptionId(subscriptions[0]);
        mLeoSrvcc = new android.telephony.PhoneStateListener(android.os.Looper.getMainLooper()) {
            public void onVoLteServiceStateChanged(android.telephony.VoLteServiceState state) {
                if (state == null) return;
                Message event = mHandler.obtainMessage(EVENT_SRVCC_STATE_CHANGED);
                AsyncResult.forMessage(event, new int[] { state.getSrvccState() }, null);
                event.sendToTarget();
            }
        };
        mLeoTelephony.listen(mLeoSrvcc, android.telephony.PhoneStateListener.LISTEN_VOLTE_STATE);
        mPhoneId = phoneId;
    }''')
    s=s.replace('listener.registrationConnected(imsRadioTech)','listener.registrationConnectedWithRadioTech(imsRadioTech)').replace('listener.registrationProgressing(imsRadioTech)','listener.registrationProgressingWithRadioTech(imsRadioTech)')
    s=method(s,'isVTGloballySupported','public boolean isVTGloballySupported() { return false; }')
    s=s.replace('public int getServiceId(int serviceClass, PendingIntent intent,\n            IImsRegistrationListener listener) {','public int getServiceId(int serviceClass, PendingIntent intent,\n            IImsRegistrationListener listener) {\n        mLeoListeners.add(listener); listener = mLeoListeners;')
    s=s.replace('tracker.mRegListener = listener;','mLeoListeners.add(listener); tracker.mRegListener = mLeoListeners;')
    pattern=r'(ImsReasonInfo imsReasonInfo = new ImsReasonInfo\(.*?\);)(\s*for \(Map.Entry<Integer, ImsServiceClassTracker> e : mTrackerTable.entrySet\(\)\))'
    assert len(re.findall(pattern,s,re.S))==1
    s=re.sub(pattern,r'\1\n            mLeoRegistrationState = regState;\n            android.os.SystemProperties.set("sys.leo.ims.registration", Integer.toString(regState));\n            Log.i(LOG_TAG, "LEO_IMS_REGISTRATION state=" + regState + " error=" + errorCode + " rat=" + imsRat);\n\2',s,flags=re.S)
    at=s.index('private void handleSrvStatusUpdate(');end=body_end(s,s.index('{',at))
    s=s[:end-1]+'''
        boolean voice = false;
        for (ServiceStatus service : mServiceStatus) {
            if (service == null || !service.isValid || service.type != ImsQmiIF.CALL_TYPE_VOICE || service.accessTechStatus == null) continue;
            for (ServiceStatus.StatusForAccessTech access : service.accessTechStatus) {
                if (access != null && access.networkMode == ImsQmiIF.RADIO_TECH_LTE && access.status == ImsQmiIF.STATUS_ENABLED) voice = true;
            }
        }
        mLeoVoiceAvailable = voice;
        android.os.SystemProperties.set("sys.leo.ims.voice", voice ? "1" : "0");
    '''+s[end-1:]
    return append(s,'''private TelephonyManager mLeoTelephony;
    private android.telephony.PhoneStateListener mLeoSrvcc;
    private final LeoRegistrationFanout mLeoListeners = new LeoRegistrationFanout();
    private volatile int mLeoRegistrationState = ImsQmiIF.Registration.NOT_REGISTERED;
    private volatile boolean mLeoVoiceAvailable;
    public boolean isVoiceReadyForLeo() { return mLeoRegistrationState == ImsQmiIF.Registration.REGISTERED && mLeoVoiceAvailable; }
    public void dumpForLeo(java.io.PrintWriter out) { out.println("LEO_IMS slot=" + LeoImsSlot.get() + " phone=" + mPhoneId + " registration=" + mLeoRegistrationState + " voice_available=" + mLeoVoiceAvailable); }
    public void closeForLeo(int serviceId) {
        ImsServiceClassTracker tracker = mServiceIdTable.remove(serviceId);
        if (tracker != null) { mTrackerTable.values().remove(tracker); tracker.mRegListener = null; tracker.mIncomingCallIntent = null; }
        if (mServiceIdTable.isEmpty()) mLeoListeners.clear();
    }
    public void addRegistrationListener(IImsRegistrationListener listener) {
        mLeoListeners.add(listener);
        mCi.getImsRegistrationState(mHandler.obtainMessage(EVENT_IMS_STATE_DONE));
    }
''')
update(ims/'ImsServiceSub.java',subscription)

# Choose the socket before starting its receiver thread; no transient SIM 1 bind.
def selected_socket(s):
    needle='mInstanceId = 0; // default socket'
    assert needle in s
    return s.replace(needle,'mInstanceId = LeoImsSlot.get(); // selected physical SIM')
update(ims/'ImsSenderRxr.java',selected_socket)

def tracker(s):
    s=re.sub(r'^.*intent.putExtra\(ImsManager.EXTRA_UNKNOWN_CALL_(?:ADDRESS|STATE),.*\n','',s,flags=re.M)
    for name,value in {'ACTIVE':0,'HOLD':1,'DIALING':2,'ALERTING':3,'INCOMING':4,'WAITING':5,'END':6}.items():s=s.replace('ImsManager.CALL_'+name,str(value))
    return s
update(ims/'ImsServiceClassTracker.java',tracker)

def proxy(s):
    for name in ('callSessionDeflected','callSessionDeflectFailed'):
        s=method(s,name,'public void '+name+'('+('IImsCallSession session' if name=='callSessionDeflected' else 'IImsCallSession session, ImsReasonInfo reason')+') { Log.w(LOG_TAG, "Legacy deflection callback has no Android 8.1 equivalent"); }')
    extra={
      'callSessionMayHandover':'IImsCallSession session, int src, int target',
      'callSessionMultipartyStateChanged':'IImsCallSession session, boolean multiparty',
      'callSessionRttModifyRequestReceived':'IImsCallSession session, ImsCallProfile profile',
      'callSessionRttModifyResponseReceived':'int status',
      'callSessionRttMessageReceived':'String message'}
    code=''
    for name,params in extra.items():
        args=', '.join(x.strip().split()[-1] for x in params.split(','))
        code+=f'public void {name}({params}) {{ if(mListener != null) try {{ mListener.{name}({args}); }} catch(RemoteException e) {{ Log.w(LOG_TAG, "Listener unavailable", e); }} }}\n'
    return append(s,code)
update(ims/'ImsCallSessionListenerProxy.java',proxy)
update(ims/'ImsCallSessionImpl.java',lambda s:append(s,'''public void sendRttModifyRequest(ImsCallProfile profile) { throw new UnsupportedOperationException("RTT is not implemented"); }
public void sendRttModifyResponse(boolean accept) { throw new UnsupportedOperationException("RTT is not implemented"); }
public void sendRttMessage(String message) { throw new UnsupportedOperationException("RTT is not implemented"); }
'''))
def voice_session(s):
    s=re.sub(r'^\s*mImsVideoCallProviderImpl = new ImsVideoCallProviderImpl\([^;]*;','',s,flags=re.M)
    s=re.sub(r'^\s*addListener\(mImsVideoCallProviderImpl\);','',s,flags=re.M)
    s=method(s,'getVideoCallProvider','public IImsVideoCallProvider getVideoCallProvider() { return null; }')
    return s
update(ims/'ImsCallSessionImpl.java',voice_session)

# Keep vendor timer metadata internal; Android's public parcels do not contain it.
for name,parent,fields in [('LeoCallForwardInfo','com.android.internal.telephony.CallForwardInfo','startHour,startMinute,endHour,endMinute'),('LeoImsCallForwardInfo','com.android.ims.ImsCallForwardInfo','mStartHour,mStartMinute,mEndHour,mEndMinute')]:
    (ims/(name+'.java')).write_text(f'package org.codeaurora.ims; public class {name} extends {parent} {{ public int {fields}; }}\n')
for name in ('ImsSenderRxr.java','ImsUtImpl.java'):
    def timers(s):
        s=s.replace('import com.android.internal.telephony.CallForwardInfo;','').replace('import com.android.ims.ImsCallForwardInfo;','')
        s=re.sub(r'\bCallForwardInfo\b','LeoCallForwardInfo',s)
        s=s.replace('ImsQmiIF.CallForwardInfoList.LeoCallForwardInfo','ImsQmiIF.CallForwardInfoList.CallForwardInfo')
        s=re.sub(r'\bImsCallForwardInfo\b','LeoImsCallForwardInfo',s)
        return s
    update(ims/name,timers)

def config(s):
    # Vendor-only telemetry/WFC callbacks are not in the public AOSP Binder API.
    s=s.replace('@Override','')
    s=re.sub(r'\bimsConfigListener\.(onGetPacketCount|onGetPacketErrorCount|onGetWifiCallingPreference|onSetWifiCallingPreference)\([^;]*;','Log.w(TAG, "Vendor-only config callback unsupported on AOSP");',s)
    s=method(s,'getVolteProvisioned','public boolean getVolteProvisioned() { return false; /* Provisioning has not been verified. */ }')
    s=method(s,'getProvisionedValue','public int getProvisionedValue(int item) { return -1; /* Unknown, not provisioned OFF. */ }')
    s=method(s,'setProvisionedValue','public int setProvisionedValue(int item, int value) { return ImsConfig.OperationStatusConstants.FAILED; }')
    s=method(s,'setProvisionedStringValue','public int setProvisionedStringValue(int item, String value) { return ImsConfig.OperationStatusConstants.FAILED; }')
    s=method(s,'getFeatureValue','''public void getFeatureValue(final int feature, final int network, final ImsConfigListener listener) {
        if (listener == null) throw new IllegalArgumentException("Listener required");
        if (feature != ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE || network != TelephonyManager.NETWORK_TYPE_LTE) {
            try { listener.onGetFeatureResponse(feature, network, -1, ImsConfig.OperationStatusConstants.FAILED); } catch (android.os.RemoteException e) { Log.w(TAG, "Listener unavailable", e); }
            return;
        }
        Handler reply = new Handler(android.os.Looper.getMainLooper()) {
            public void handleMessage(Message message) {
                AsyncResult result = (AsyncResult) message.obj;
                int value = -1, status = ImsConfig.OperationStatusConstants.FAILED;
                if (result != null && result.exception == null && result.result instanceof java.util.ArrayList) {
                    for (Object item : (java.util.ArrayList<?>) result.result) {
                        if (!(item instanceof ServiceStatus)) continue;
                        ServiceStatus service = (ServiceStatus) item;
                        if (!service.isValid || service.type != ImsQmiIF.CALL_TYPE_VOICE || service.accessTechStatus == null) continue;
                        for (ServiceStatus.StatusForAccessTech access : service.accessTechStatus) {
                            if (access != null && access.networkMode == ImsQmiIF.RADIO_TECH_LTE) {
                                value = access.status == ImsQmiIF.STATUS_ENABLED ? ImsConfig.FeatureValueConstants.ON : ImsConfig.FeatureValueConstants.OFF;
                                status = ImsConfig.OperationStatusConstants.SUCCESS;
                            }
                        }
                    }
                }
                try { listener.onGetFeatureResponse(feature, network, value, status); } catch (android.os.RemoteException e) { Log.w(TAG, "Listener unavailable", e); }
            }
        };
        mCi.queryServiceStatus(reply.obtainMessage(1));
    }''')
    needle='public void setFeatureValue(int feature, int network, int value, ImsConfigListener listener) {'
    assert needle in s
    s=s.replace(needle,needle+'''
        if (feature != ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE || network != TelephonyManager.NETWORK_TYPE_LTE || (value != 0 && value != 1)) {
            if (listener != null) try { listener.onSetFeatureResponse(feature, network, value, ImsConfig.OperationStatusConstants.FAILED); } catch (android.os.RemoteException e) { Log.w(TAG, "Listener unavailable", e); }
            return;
        }
''')
    return s
update(ims/'ImsConfigImpl.java',config)

def video(s):
    s=s.replace('public void onSendSessionModifyRequest(VideoProfile requestProfile)','public void onSendSessionModifyRequest(VideoProfile fromProfile, VideoProfile requestProfile)')
    s=s.replace('public void onSetPauseImage(String uri)','public void onSetPauseImage(android.net.Uri uri)')
    return s
update(root/'com/qualcomm/ims/vt/ImsVideoCallProviderImpl.java',video)
update(root/'com/qualcomm/ims/vt/CameraController.java',lambda s:s.replace('new CameraCapabilities(isZoomSupported, maxZoom,\n                        size.getWidth(), size.getHeight())','new CameraCapabilities(size.getWidth(), size.getHeight(), isZoomSupported, maxZoom)'))

# Remove unbuilt CSVT entry points and require the phone control permission.
android='{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android',android[1:-1])
manifest=root.parent/'AndroidManifest.xml'
tree=ET.parse(manifest);app=tree.getroot().find('application')
sdk=tree.getroot().find('uses-sdk');sdk.set(android+'minSdkVersion','27');sdk.set(android+'targetSdkVersion','27')
tree.getroot().set(android+'versionCode','3');tree.getroot().set(android+'versionName','1.2-runtime')
for child in list(app):
    if 'csvt' in child.get(android+'name','').lower():app.remove(child)
for child in app.findall('service'):
    child.set(android+'permission','android.permission.MODIFY_PHONE_STATE')
    child.set(android+'process',':leo_ims')
for permission in ('MODIFY_PHONE_STATE','READ_PHONE_STATE'):
    ET.SubElement(tree.getroot(),'uses-permission',{android+'name':'android.permission.'+permission})
(ims/'LeoImsController.java').write_text((runtime/'LeoImsController.java').read_text())
(ims/'ImsRecoveryPolicy.java').write_text((runtime/'ImsRecoveryPolicy.java').read_text())
ET.SubElement(app,'service',{
    android+'name':'.LeoImsController',android+'exported':'true',
    android+'permission':'android.permission.MODIFY_PHONE_STATE',android+'process':':leo_controller'})
tree.write(manifest,encoding='utf-8',xml_declaration=True)
print('Applied selected-SIM Android 8.1 compatibility port; no automatic activation.')
