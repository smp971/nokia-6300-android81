package org.codeaurora.ims;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.android.ims.internal.IImsService;

/** One selected physical IMS slot. No forced radio restart and no synthetic registration. */
public final class LeoImsController extends Service {
    private static final String TAG="LeoImsController";
    private final Handler handler=new Handler();
    private int active=-1;
    private boolean stopping;
    private long lastOff, lastConfigure;
    private String lastState="";
    private SharedPreferences saved;
    private final Runnable tick=new Runnable(){public void run(){
        try { step(); }
        catch(Exception error){state("controller_error");Log.e(TAG,"IMS controller retry",error);}
        handler.postDelayed(this,stopping?2000:10000);
    }};
    private final BroadcastReceiver changes=new BroadcastReceiver(){
        public void onReceive(Context c,Intent i){handler.removeCallbacks(tick);handler.post(tick);}
    };
    public void onCreate(){
        super.onCreate();saved=getSharedPreferences("leo_ims_runtime",MODE_PRIVATE);
        IBinder binder=ServiceManager.checkService("ims");
        if(binder!=null && binder.pingBinder())active=SystemProperties.getInt("sys.leo.ims.client_slot",-1);
        // Recover controller state without disturbing an existing client/call.
        if(active<0 && SystemProperties.getBoolean("sys.leo.ims.native",false))
            active=SystemProperties.getInt("sys.leo.ims.slot",-1);
        IntentFilter filter=new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction("android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED");
        filter.addAction("android.intent.action.SIM_STATE_CHANGED");
        registerReceiver(changes,filter);
    }
    public int onStartCommand(Intent i,int flags,int id){handler.removeCallbacks(tick);handler.post(tick);return START_STICKY;}
    public IBinder onBind(Intent i){return null;}
    public void onDestroy(){handler.removeCallbacks(tick);unregisterReceiver(changes);super.onDestroy();}
    private void state(String value){
        if(!value.equals(lastState)){
            lastState=value;SystemProperties.set("sys.leo.ims.state",value);
            Log.i(TAG,value+" slot="+active);
        }
    }
    private boolean busy(){
        TelecomManager telecom=(TelecomManager)getSystemService(TELECOM_SERVICE);
        return telecom==null || telecom.isInCall();
    }
    private IImsService client(){
        IBinder binder=ServiceManager.checkService("ims");
        return binder!=null && binder.pingBinder()?IImsService.Stub.asInterface(binder):null;
    }
    private void overrideOn(){
        if(!saved.contains("availability_before"))
            saved.edit().putString("availability_before",SystemProperties.get("persist.dbg.volte_avail_ovr","")).commit();
        SystemProperties.set("persist.dbg.volte_avail_ovr","1");
    }
    private void overrideOff(){
        if(saved.contains("availability_before")){
            SystemProperties.set("persist.dbg.volte_avail_ovr",saved.getString("availability_before",""));
            saved.edit().remove("availability_before").commit();
        }
    }
    private void down(int slot){
        Intent intent=new Intent("com.android.ims.IMS_SERVICE_DOWN");
        intent.setPackage("com.android.phone");intent.putExtra("android:phone_id",slot);sendBroadcast(intent);
    }
    private void beginStop(){
        if(busy()){state("call_deferred");return;}
        stopping=true;
        long now=SystemClock.elapsedRealtime();
        IImsService ims=client();
        if(ims!=null && (lastOff==0 || now-lastOff>=30000)){
            try {ims.turnOffIms(active);lastOff=now;}
            catch(android.os.RemoteException gone){Log.i(TAG,"Client disconnected during shutdown");}
        }
        if(ims!=null && SystemProperties.getInt("sys.leo.ims.registration",2)!=2){
            state("deregistering");return;
        }
        // Check again immediately before destroying the selected service.
        if(busy()){state("call_deferred");return;}
        down(active);
        stopService(new Intent(this,ImsService.class));
        SystemProperties.set("sys.leo.ims.native","0");
        active=-1;stopping=false;lastOff=0;lastConfigure=0;
        state("stopped");
    }
    private void startClient(){
        if(busy()){state("call_deferred");return;}
        overrideOn();
        Intent intent=new Intent(this,ImsService.class);intent.putExtra("leoReconfigure",true);
        startService(intent);lastConfigure=SystemClock.elapsedRealtime();state("registering");
    }
    private void step() throws Exception {
        boolean enabled=SystemProperties.getBoolean("persist.leo.ims.enabled",false);
        boolean airplane=Settings.Global.getInt(getContentResolver(),Settings.Global.AIRPLANE_MODE_ON,0)!=0;
        SubscriptionManager subscriptions=(SubscriptionManager)getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE);
        SubscriptionInfo info=subscriptions.getActiveSubscriptionInfo(SubscriptionManager.getDefaultDataSubscriptionId());
        int desired=info==null?-1:info.getSimSlotIndex();
        if(desired<0 || desired>1)desired=-1;
        if(stopping || (active>=0 && (!enabled || airplane || (desired>=0 && desired!=active)))){
            beginStop();return;
        }
        if(!enabled || airplane){
            if(busy()){state("call_deferred");return;}
            SystemProperties.set("sys.leo.ims.native","0");overrideOff();state(enabled?"airplane":"disabled");
            return;
        }
        if(active<0){
            if(desired<0 || !"started".equals(SystemProperties.get("sys.leo.radio.start_state"))){state("waiting_sim");return;}
            TelephonyManager phone=((TelephonyManager)getSystemService(TELEPHONY_SERVICE)).createForSubscriptionId(info.getSubscriptionId());
            if(phone.getDataNetworkType()!=TelephonyManager.NETWORK_TYPE_LTE && phone.getVoiceNetworkType()!=TelephonyManager.NETWORK_TYPE_LTE){state("waiting_lte");return;}
            if(busy()){state("call_deferred");return;}
            // Complete the previous native teardown before selecting another socket.
            if(SystemProperties.getBoolean("sys.leo.ims.native_ready",false)){state("waiting_native_stop");return;}
            active=desired;SystemProperties.set("sys.leo.ims.slot",Integer.toString(active));
            SystemProperties.set("sys.leo.ims.registration","2");SystemProperties.set("sys.leo.ims.voice","0");
            SystemProperties.set("sys.leo.ims.native","1");state("starting_native");return;
        }
        if(!SystemProperties.getBoolean("sys.leo.ims.native_ready",false)){state("waiting_native");return;}
        if(client()==null){startClient();return;}
        if(SystemProperties.getInt("sys.leo.ims.registration",2)==1 && SystemProperties.getBoolean("sys.leo.ims.voice",false)){
            state("registered");return;
        }
        if(!busy() && SystemClock.elapsedRealtime()-lastConfigure>=30000)startClient();
    }
}
