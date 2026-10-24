package ro.leo.visual;
import ro.leo.i18n.L;

import android.content.Context;
import android.telephony.*;
import java.util.*;

/** Event-driven, per-SIM network state. No cell scan, GPS or background polling. */
final class NetworkTelemetry {
    private final Context context;
    private final Runnable changed;
    private final List<TelephonyManager> phones=new ArrayList<TelephonyManager>();
    private final List<PhoneStateListener> listeners=new ArrayList<PhoneStateListener>();
    private final String[] rows={L.t("SIM 1 · se verifică"),L.t("SIM 2 · se verifică")};
    private SubscriptionManager subscriptions;
    private final SubscriptionManager.OnSubscriptionsChangedListener subscriptionListener=new SubscriptionManager.OnSubscriptionsChangedListener(){public void onSubscriptionsChanged(){bind();}};
    NetworkTelemetry(Context c,Runnable callback){context=c;changed=callback;}
    void start(){subscriptions=(SubscriptionManager)context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);try{subscriptions.addOnSubscriptionsChangedListener(subscriptionListener);}catch(RuntimeException ignored){}bind();}
    void stop(){if(subscriptions!=null)try{subscriptions.removeOnSubscriptionsChangedListener(subscriptionListener);}catch(RuntimeException ignored){}clear();}
    private void clear(){for(int i=0;i<phones.size();i++)try{phones.get(i).listen(listeners.get(i),PhoneStateListener.LISTEN_NONE);}catch(RuntimeException ignored){}phones.clear();listeners.clear();}
    private void bind(){
        clear();TelephonyManager base=(TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
        for(int slot=0;slot<2;slot++)try{rows[slot]="SIM "+(slot+1)+(base.getSimState(slot)==TelephonyManager.SIM_STATE_ABSENT?L.t(" · lipsă"):L.t(" · fără serviciu"));}catch(RuntimeException e){rows[slot]="SIM "+(slot+1)+L.t(" · indisponibil");}
        try{List<SubscriptionInfo> active=subscriptions.getActiveSubscriptionInfoList();if(active!=null)for(final SubscriptionInfo info:active){
            final int slot=info.getSimSlotIndex();if(slot<0||slot>1)continue;
            final TelephonyManager phone=base.createForSubscriptionId(info.getSubscriptionId());
            PhoneStateListener listener=new PhoneStateListener(){
                int dbm=Integer.MIN_VALUE;
                private void update(){
                    try{String name=phone.getNetworkOperatorName();if(name==null||name.isEmpty())name="Operator";ServiceState state=phone.getServiceState();String status=L.t("fără serviciu");
                        if(state!=null){if(state.getState()==ServiceState.STATE_IN_SERVICE){int type=phone.getDataNetworkType();if(type==TelephonyManager.NETWORK_TYPE_UNKNOWN)type=phone.getVoiceNetworkType();status=rat(type)+(dbm!=Integer.MIN_VALUE?" · "+dbm+" dBm":"");}else if(state.getState()==ServiceState.STATE_EMERGENCY_ONLY)status=L.t("doar urgențe");else if(state.getState()==ServiceState.STATE_POWER_OFF)status=L.t("radio oprit");}
                        rows[slot]="SIM "+(slot+1)+" · "+name+" · "+status+(ImsStatus.registered(slot)?" · VoLTE":"");
                    }catch(RuntimeException e){rows[slot]="SIM "+(slot+1)+L.t(" · indisponibil");}changed.run();
                }
                public void onServiceStateChanged(ServiceState s){update();}
                public void onDataConnectionStateChanged(int state,int type){update();}
                public void onSignalStrengthsChanged(SignalStrength strength){dbm=dbm(strength);update();}
            };
            phones.add(phone);listeners.add(listener);phone.listen(listener,PhoneStateListener.LISTEN_SERVICE_STATE|PhoneStateListener.LISTEN_DATA_CONNECTION_STATE|PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        }}catch(RuntimeException ignored){}changed.run();
    }
    String summary(){return rows[0]+"\n"+rows[1];}
    static int dbm(SignalStrength strength){
        if(strength==null)return Integer.MIN_VALUE;
        try{int value=(Integer)SignalStrength.class.getMethod("getDbm").invoke(strength);if(value>=-160&&value<=-20)return value;}catch(Exception ignored){}
        // GSM ASU is valid only for GSM; do not label a fallback GSM value as LTE.
        return Integer.MIN_VALUE;
    }
    static String rat(int type){switch(type){case TelephonyManager.NETWORK_TYPE_LTE:return "4G";case TelephonyManager.NETWORK_TYPE_GPRS:case TelephonyManager.NETWORK_TYPE_EDGE:case TelephonyManager.NETWORK_TYPE_GSM:return "2G";case TelephonyManager.NETWORK_TYPE_UMTS:case TelephonyManager.NETWORK_TYPE_HSDPA:case TelephonyManager.NETWORK_TYPE_HSUPA:case TelephonyManager.NETWORK_TYPE_HSPA:case TelephonyManager.NETWORK_TYPE_HSPAP:return "3G";default:return L.t("conectat");}}
}
