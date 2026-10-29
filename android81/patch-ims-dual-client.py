"""Experimental dual-physical-slot extension, applied after the pinned Oreo port.

Builds a separate candidate; never installs, enables properties or changes NV.
Android IImsMultiEndpoint (external call continuity) remains unsupported.
"""
from pathlib import Path
import argparse
import re
import shutil
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise ValueError('Unexpected source anchor: ' + old[:100])
    return text.replace(old, new)


def method(text, name, replacement):
    pattern = r'(?:@Override\s*)?(?:(?:public|private|protected)\s+)?(?:static\s+)?(?:synchronized\s+)?(?:final\s+)?[\w<>\[\]]+\s+' + name + r'\s*\([^)]*\)\s*(?:throws[^{}]+)?\{'
    matches = list(re.finditer(pattern, text))
    if len(matches) != 1:
        raise ValueError('Unexpected method count: ' + name)
    m = matches[0]
    start = text.index('{', m.start())
    depth = 0
    for token in re.finditer(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"|\x27(?:\\.|[^\x27\\])*\x27|[{}]', text[start:], re.S):
        if token.group() == '{':
            depth += 1
        elif token.group() == '}':
            depth -= 1
            if depth == 0:
                return text[:m.start()] + replacement + text[start+token.end():]
    raise ValueError('Unbalanced Java method: ' + name)


def append(text, extra):
    at = text.rfind('}')
    return text[:at] + extra + '\n' + text[at:]


def patch(root):
    ims = root/'src/org/codeaurora/ims'
    for name in ['LeoImsTopology.java']:
        shutil.copyfile(ROOT/'ims-runtime'/name, ims/name)
    slot = ims/'LeoImsSlot.java'
    slot.write_text('''package org.codeaurora.ims;
final class LeoImsSlot {
    // Topology is fixed for this process. Changing the property needs a clean stop.
    private static final LeoImsTopology TOPOLOGY = new LeoImsTopology(
            android.os.SystemProperties.getBoolean("sys.leo.ims.dual",false),get());
    static int get(){int slot=android.os.SystemProperties.getInt("sys.leo.ims.slot",0);
        if(slot<0||slot>1)throw new IllegalArgumentException("Invalid physical IMS slot");return slot;}
    static LeoImsTopology topology(){return TOPOLOGY;}
}
''', encoding='utf-8')

    path = ims/'ImsSenderRxr.java'
    text = path.read_text()
    text = replace_once(text, 'public ImsSenderRxr(Context context)', 'public ImsSenderRxr(Context context, int physicalSlot)')
    text = replace_once(text, 'mInstanceId = LeoImsSlot.get(); // selected physical SIM',
                        'if (LeoImsSlot.topology().indexOf(physicalSlot)<0) throw new IllegalArgumentException("Inactive physical IMS slot");\n        mInstanceId = physicalSlot; // fixed before receiver thread starts')
    text = method(text, 'registerForPhoneId', '''public void registerForPhoneId(int phoneId) {
        if(phoneId != mInstanceId)throw new IllegalStateException("A transport cannot change physical slot");
    }''')
    # Requests share a synchronized global counter, but queues are per socket.
    # A reconnect on SIM 1 must not reset IDs of requests still pending on SIM 2.
    text = method(text, 'resetSerial', 'static void resetSerial() { /* Keep monotonically assigned IDs across socket reconnects. */ }')
    path.write_text(text)

    path = ims/'ImsUtImpl.java'
    text = path.read_text()
    text = replace_once(text, 'private static ImsUtImpl sUtImpl = null;', 'private static final java.util.Map<ImsSenderRxr,ImsUtImpl> sUt = new java.util.IdentityHashMap<>();')
    text = method(text, 'createUtInterface', '''public static synchronized void createUtInterface(ImsSenderRxr transport) {
        if(transport==null)throw new IllegalArgumentException("UT transport required");
        if(!sUt.containsKey(transport))sUt.put(transport,new ImsUtImpl(transport));
    }''')
    text = method(text, 'getUtInterface', '''public static synchronized ImsUtImpl getUtInterface(ImsSenderRxr transport) {
        return sUt.get(transport);
    }''')
    text = replace_once(text, 'private static int requestId = -1;', 'private int requestId = -1;')
    text = replace_once(text, 'private int getIdForRequest()', 'private synchronized int getIdForRequest()')
    path.write_text(text)

    path = ims/'ImsServiceSub.java'
    text = path.read_text()
    text = replace_once(text, 'public ImsServiceSub(int sub, Context context)', 'public ImsServiceSub(int sub, Context context, int physicalSlot)')
    text = replace_once(text, 'mCi = new ImsSenderRxr(mContext);', 'mLeoPhysicalSlot=physicalSlot;\n        mCi = new ImsSenderRxr(mContext,physicalSlot);')
    text = replace_once(text, 'private int mPhoneId = -1;', 'private int mPhoneId = -1;\n    private final int mLeoPhysicalSlot;')
    text = replace_once(text, 'if (phoneId != LeoImsSlot.get()) throw new IllegalArgumentException("Only selected SIM supported");', 'if(phoneId!=mLeoPhysicalSlot)throw new IllegalArgumentException("Physical slot remapping is forbidden");')
    # No active subscription is normal for an empty second slot; do not crash SIM 1.
    text = replace_once(text, 'if (subscriptions == null || subscriptions.length == 0) throw new IllegalStateException("SIM not ready");', 'if(subscriptions==null||subscriptions.length==0){mPhoneId=phoneId;return;}')
    text = method(text, 'registerForPhoneId', '''public void registerForPhoneId(int phoneId) {
        setPhoneId(phoneId); mCi.registerForPhoneId(phoneId);
    }''')
    text = replace_once(text, 'return ImsUtImpl.getUtInterface();', 'return ImsUtImpl.getUtInterface(mCi);')
    for key, value in [('registration','Integer.toString(regState)'), ('voice','voice ? "1" : "0"')]:
        old = 'android.os.SystemProperties.set("sys.leo.ims.'+key+'", '+value+');'
        per = 'reg' if key=='registration' else 'voice'
        text = replace_once(text, old, 'android.os.SystemProperties.set("sys.leo.ims.'+per+'"+mLeoPhysicalSlot, '+value+');\n            if(mLeoPhysicalSlot==LeoImsSlot.get()) '+old)
    text = text.replace('"LEO_IMS slot=" + LeoImsSlot.get()', '"LEO_IMS slot=" + mLeoPhysicalSlot')
    text = method(text, 'dumpForLeo', '''public void dumpForLeo(java.io.PrintWriter out){
        out.println("LEO_IMS slot="+mLeoPhysicalSlot+" phone="+mPhoneId+" registration="+mLeoRegistrationState+" voice_available="+mLeoVoiceAvailable);
        if(mServiceStatus!=null)for(ServiceStatus service:mServiceStatus){
            if(service==null||!service.isValid||service.accessTechStatus==null)continue;
            for(ServiceStatus.StatusForAccessTech access:service.accessTechStatus)if(access!=null)
                out.println("LEO_IMS_CAP slot="+mLeoPhysicalSlot+" type="+service.type+" rat="+access.networkMode+" status="+access.status+" restriction="+access.restrictCause);
        }
    }''')
    text = text.replace('new HashMap<Integer, ImsServiceClassTracker>()', 'new java.util.concurrent.ConcurrentHashMap<Integer, ImsServiceClassTracker>()')
    path.write_text(text)

    path = ims/'ImsServiceClassTracker.java'
    text = replace_once(path.read_text(), 'private static int createServiceId()', 'private static synchronized int createServiceId()')
    path.write_text(text)

    path = ims/'ImsService.java'
    text = path.read_text()
    text = replace_once(text, 'private static final int MAX_SUBSCRIPTIONS = 1;', '''private final LeoImsTopology mLeoTopology=LeoImsSlot.topology();
    private final boolean mLeoReadOnly=android.os.SystemProperties.getBoolean("sys.leo.ims.readonly",false);''')
    text = text.replace('new HashMap<Integer, ImsServiceSub>()', 'new java.util.concurrent.ConcurrentHashMap<Integer, ImsServiceSub>()')
    text = method(text, 'getNumSubscriptions', 'private int getNumSubscriptions(){return mLeoTopology.count();}')
    text = replace_once(text, 'new ImsServiceSub(i + 1, this)', 'new ImsServiceSub(i+1,this,mLeoTopology.phoneAt(i))')
    text = method(text, 'initSubscriptionStatus', '''private void initSubscriptionStatus(){
        for(int i=0;i<mServiceSub.length;i++)mServiceSub[i].registerForPhoneId(mLeoTopology.phoneAt(i));
    }''')
    # Publish per-slot DOWN/UP notifications, not one replace-pending sticky intent
    # where SIM 2 would overwrite SIM 1's notification.
    text = method(text, 'onCreate', '''public void onCreate(){
        super.onCreate();mImsPhoneId=LeoImsSlot.get();
        android.os.SystemProperties.set("sys.leo.ims.registration","2");
        android.os.SystemProperties.set("sys.leo.ims.voice","0");
        mServiceSub=new ImsServiceSub[mLeoTopology.count()];
        for(int i=0;i<mServiceSub.length;i++){
            int slot=mLeoTopology.phoneAt(i);
            android.os.SystemProperties.set("sys.leo.ims.reg"+slot,"2");
            android.os.SystemProperties.set("sys.leo.ims.voice"+slot,"0");
            mServiceSub[i]=new ImsServiceSub(i+1,this,slot);
        }
        initSubscriptionStatus();
        for(int i=0;i<mServiceSub.length;i++)publishForLeo(ImsManager.ACTION_IMS_SERVICE_DOWN,mLeoTopology.phoneAt(i));
        if(!mLeoReadOnly)ServiceManager.addService("ims",mBinder);
        android.os.SystemProperties.set("sys.leo.ims.client_slot",mLeoTopology.dual()?"dual":Integer.toString(mImsPhoneId));
        for(int i=0;i<mServiceSub.length;i++)publishForLeo(ImsManager.ACTION_IMS_SERVICE_UP,mLeoTopology.phoneAt(i));
    }''')
    text = method(text, 'onDestroy', '''public void onDestroy(){
        for(int i=0;i<mServiceSub.length;i++){
            int slot=mLeoTopology.phoneAt(i);publishForLeo(ImsManager.ACTION_IMS_SERVICE_DOWN,slot);
            android.os.SystemProperties.set("sys.leo.ims.reg"+slot,"2");
            android.os.SystemProperties.set("sys.leo.ims.voice"+slot,"0");
        }
        android.os.SystemProperties.set("sys.leo.ims.client_slot","-1");
        android.os.SystemProperties.set("sys.leo.ims.registration","2");
        android.os.SystemProperties.set("sys.leo.ims.voice","0");super.onDestroy();
        new Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable(){public void run(){android.os.Process.killProcess(android.os.Process.myPid());}},100);
    }''')
    text = method(text, 'onBind', '''public IBinder onBind(Intent intent){return mLeoReadOnly?null:mBinder;}''')
    text = method(text, 'open', '''public int open(int phoneId,int serviceClass,PendingIntent intent,IImsRegistrationListener listener){
        int index=mLeoTopology.indexOf(phoneId);
        if(index<0||serviceClass!=ImsServiceClass.MMTEL)return -1;
        return openForSub(index+1,serviceClass,intent,listener);
    }''')
    text = method(text, 'openForSub', '''public int openForSub(int endpoint,int serviceClass,PendingIntent intent,IImsRegistrationListener listener){
        if(endpoint<1||endpoint>mServiceSub.length||serviceClass!=ImsServiceClass.MMTEL)return -1;
        ImsServiceSub service=mServiceSub[endpoint-1];
        int id=service.getServiceId(serviceClass,intent,listener);
        if(id>0)mServiceSubMap.put(id,service);return id;
    }''')
    text = method(text, 'addRegistrationListener', '''public void addRegistrationListener(int phoneId,int serviceClass,IImsRegistrationListener listener){
        int index=mLeoTopology.indexOf(phoneId);
        if(index<0||serviceClass!=ImsServiceClass.MMTEL)throw new IllegalArgumentException("Inactive MMTEL slot");
        mServiceSub[index].addRegistrationListener(listener);
    }''')
    text = method(text, 'getConfigInterface', '''public IImsConfig getConfigInterface(int phoneId){
        int index=mLeoTopology.indexOf(phoneId);return index<0?null:mServiceSub[index].getConfigInterface();
    }''')
    for name, operation in [('turnOnIms','turnOnIms'),('turnOffIms','turnOffIms')]:
        text = method(text, name, 'public void '+name+'(int phoneId){int index=mLeoTopology.indexOf(phoneId);if(index>=0)mServiceSub[index].'+operation+'();}')
    text = method(text, 'onStartCommand', '''public int onStartCommand(Intent intent,int flags,int startId){
        if(mLeoReadOnly){for(ImsServiceSub service:mServiceSub)service.refreshForLeo();return START_NOT_STICKY;}
        if(intent!=null&&intent.getBooleanExtra("leoReconfigure",false)){
            if(((android.telecom.TelecomManager)getSystemService(TELECOM_SERVICE)).isInCall())return START_NOT_STICKY;
            for(int i=0;i<mServiceSub.length;i++){
                int phone=mLeoTopology.phoneAt(i);int[] ids=android.telephony.SubscriptionManager.getSubId(phone);
                if(ids==null||ids.length==0)continue;
                ImsManager.updateImsServiceConfig(this,phone,true);mServiceSub[i].refreshForLeo();
            }
        }
        return START_NOT_STICKY;
    }''')
    text = method(text, 'dump', '''protected void dump(java.io.FileDescriptor fd,java.io.PrintWriter out,String[] args){
        for(ImsServiceSub service:mServiceSub)service.dumpForLeo(out);
    }''')
    text = append(text, '''private void publishForLeo(String action,int phone){
        if(mLeoReadOnly)return;
        Intent intent=new Intent(action);intent.setPackage("com.android.phone");
        intent.putExtra(ImsManager.EXTRA_PHONE_ID,phone);sendBroadcast(intent);
    }
''')
    path.write_text(text)

    # Candidate activation is manual: the selected-slot controller must never
    # mistake a dual Binder for a failed single-slot client and dismantle it.
    path=ims/'LeoImsController.java'
    text=replace_once(path.read_text(),'private void step() throws Exception {','''private void step() throws Exception {
        if(SystemProperties.getBoolean("sys.leo.ims.dual",false)){
            boolean disable=!SystemProperties.getBoolean("persist.leo.ims.enabled",false)
                || Settings.Global.getInt(getContentResolver(),Settings.Global.AIRPLANE_MODE_ON,0)!=0;
            if(disable){
                if(busy()){state("call_deferred");return;}
                IImsService ims=client();
                if(ims!=null){
                    ims.turnOffIms(0);ims.turnOffIms(1);
                    if(SystemProperties.getInt("sys.leo.ims.reg0",2)!=2
                        ||SystemProperties.getInt("sys.leo.ims.reg1",2)!=2){state("dual_deregistering");return;}
                }
                if(busy()){state("call_deferred");return;}
                down(0);down(1);stopService(new Intent(this,ImsService.class));
                SystemProperties.set("sys.leo.ims.native","0");overrideOff();state("dual_stopped");
            }else state("dual_manual_trial");
            return;
        }
''')
    path.write_text(text)
    ns='{http://schemas.android.com/apk/res/android}'
    ET.register_namespace('android',ns[1:-1])
    manifest=root/'AndroidManifest.xml'
    tree=ET.parse(manifest)
    tree.getroot().set(ns+'versionCode','4')
    tree.getroot().set(ns+'versionName','1.3-dual-experimental')
    tree.write(manifest,encoding='utf-8',xml_declaration=True)
    print('Dual-slot client candidate prepared; automatic dual activation intentionally absent')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,required=True)
    patch(parser.parse_args().root)
