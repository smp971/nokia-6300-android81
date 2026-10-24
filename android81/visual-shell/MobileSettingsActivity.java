package ro.leo.visual;
import ro.leo.i18n.L;

import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.telecom.*;
import android.telephony.*;
import java.util.*;

/** SIM preferences through Oreo services; no calls, messages or modem probes. */
public final class MobileSettingsActivity extends LeoActivity {
    private SubscriptionManager subscriptions;
    private TelephonyManager telephony;
    private TelecomManager telecom;
    private final BroadcastReceiver updates=new BroadcastReceiver(){public void onReceive(Context c,Intent i){show();}};
    protected void onCreate(Bundle state) {
        super.onCreate(state);subscriptions=SubscriptionManager.from(this);
        telephony=(TelephonyManager)getSystemService(TELEPHONY_SERVICE);
        telecom=(TelecomManager)getSystemService(TELECOM_SERVICE);
    }
    protected void onResume(){super.onResume();IntentFilter f=new IntentFilter();f.addAction("android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED");f.addAction("android.intent.action.ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED");f.addAction("android.intent.action.ACTION_SUBINFO_RECORD_UPDATED");registerReceiver(updates,f);show();}
    protected void onPause(){unregisterReceiver(updates);super.onPause();}
    private List<SubscriptionInfo> sims() {
        List<SubscriptionInfo> result=subscriptions.getActiveSubscriptionInfoList();
        if(result==null) return new ArrayList<>();
        result=new ArrayList<>(result);Collections.sort(result,new Comparator<SubscriptionInfo>(){public int compare(SubscriptionInfo a,SubscriptionInfo b){return Integer.compare(a.getSimSlotIndex(),b.getSimSlotIndex());}});return result;
    }
    private String simName(int id) {
        for(SubscriptionInfo s:sims()) if(s.getSubscriptionId()==id) return "SIM "+(s.getSimSlotIndex()+1);
        return id>=0?L.t("Se inițializează"):L.t("Neales");
    }
    private TelephonyManager dataPhone(){return telephony.createForSubscriptionId(SubscriptionManager.getDefaultDataSubscriptionId());}
    private String accountName(PhoneAccountHandle handle) {
        if(handle==null)return L.t("Întreabă mereu");
        PhoneAccount account=telecom.getPhoneAccount(handle);return account==null?L.t("Indisponibil"):account.getLabel().toString();
    }
    private PhoneAccountHandle selectedAccount() throws Exception {
        return (PhoneAccountHandle)telecom.getClass().getMethod("getUserSelectedOutgoingPhoneAccount").invoke(telecom);
    }
    private void show() {
        try {
            final List<SubscriptionInfo> active=sims();
            String account;try{account=accountName(selectedAccount());}catch(Exception unavailable){account=L.t("Vezi Android");}
            boolean enabled=dataPhone().isDataEnabled();
            ArrayList<String> rows=new ArrayList<>(Arrays.asList(L.t("Apeluri · ")+account,"SMS · "+simName(SubscriptionManager.getDefaultSmsSubscriptionId()),L.t("Date · ")+simName(SubscriptionManager.getDefaultDataSubscriptionId()),L.t("Date mobile · ")+(enabled?L.t("Pornite"):L.t("Oprite")),L.t("Puncte de acces (APN)"),L.t("Rețea și operator Android")));
            for(SubscriptionInfo s:active) rows.add("SIM "+(s.getSimSlotIndex()+1)+" · "+s.getCarrierName());
            list(L.t("SIM-uri și date"),rows,0,new Choice(){public void pick(int i){
                if(i==0) calls();else if(i==1||i==2) chooseSim(i==2);
                else if(i==3) changeData();else if(i==4) androidPage(Settings.ACTION_APN_SETTINGS);else if(i==5) androidPage(Settings.ACTION_NETWORK_OPERATOR_SETTINGS);
                else if(i-6<active.size()) details(active.get(i-6));
            }},L.t("Actualiz."),new Runnable(){public void run(){show();}});
        }catch(RuntimeException unavailable){begin(L.t("SIM-uri și date"));root.addView(text(L.t("Serviciul SIM nu este pregătit. Revino după inițializarea radio."),18));footer("","",L.t("Înapoi"));}
    }
    private boolean idle() {
        if(telecom.isInCall()){notice(L.t("Încheie apelul înainte de schimbarea SIM-ului"));return false;}return true;
    }
    private void chooseSim(final boolean data) {
        if(!idle())return;
        final List<SubscriptionInfo> active=sims();if(active.isEmpty()){notice(L.t("Niciun SIM activ"));return;}
        String[] labels=new String[active.size()];for(int i=0;i<labels.length;i++)labels[i]="SIM "+(active.get(i).getSimSlotIndex()+1)+" · "+active.get(i).getCarrierName();
        options(data?L.t("SIM pentru date"):L.t("SIM pentru SMS"),labels,new Choice(){public void pick(final int i){
            if(data && active.get(i).getSubscriptionId()!=SubscriptionManager.getDefaultDataSubscriptionId()) {
                options(L.t("Schimbă SIM pentru date?"),new String[]{L.t("Anulează"),L.t("Schimbă · conexiunea se reface")},new Choice(){public void pick(int yes){if(yes==1)setSim(true,active.get(i).getSubscriptionId());}});
            } else setSim(data,active.get(i).getSubscriptionId());
        }});
    }
    private void setSim(boolean data,int id) {
        if(!idle())return;
        boolean active=false;for(SubscriptionInfo sim:sims())if(sim.getSubscriptionId()==id)active=true;
        if(!active){notice(L.t("SIM-ul ales nu mai este activ"));show();return;}
        try {
            if(data && id==SubscriptionManager.getDefaultDataSubscriptionId()){notice(L.t("Acest SIM este deja ales"));return;}
            subscriptions.getClass().getMethod(data?"setDefaultDataSubId":"setDefaultSmsSubId",int.class).invoke(subscriptions,id);
            notice(L.t("Preferință trimisă către Android"));show();
        }catch(Exception failure){notice(L.t("Preferința SIM nu poate fi schimbată"));}
    }
    private void calls() {
        if(!idle())return;
        final List<PhoneAccountHandle> accounts=telecom.getCallCapablePhoneAccounts();
        String[] labels=new String[accounts.size()+1];labels[0]=L.t("Întreabă mereu");
        for(int i=0;i<accounts.size();i++)labels[i+1]=accountName(accounts.get(i));
        options(L.t("SIM pentru apeluri"),labels,new Choice(){public void pick(int i){
            if(!idle())return;
            try{PhoneAccountHandle chosen=i==0?null:accounts.get(i-1);telecom.getClass().getMethod("setUserSelectedOutgoingPhoneAccount",PhoneAccountHandle.class).invoke(telecom,chosen);notice(java.util.Objects.equals(chosen,selectedAccount())?L.t("Preferință apeluri salvată"):L.t("Preferința nu a fost confirmată"));show();}
            catch(Exception failure){notice(L.t("Preferința nu poate fi schimbată"));}
        }});
    }
    private void changeData() {
        if(!idle())return;
        if(SubscriptionManager.getDefaultDataSubscriptionId()<0){notice(L.t("Alege întâi un SIM pentru date"));return;}
        final boolean enable=!dataPhone().isDataEnabled();
        options(enable?L.t("Pornește datele mobile?"):L.t("Oprește datele mobile?"),new String[]{L.t("Anulează"),enable?L.t("Pornește"):L.t("Oprește")},new Choice(){public void pick(int i){
            if(i!=1||!idle())return;
            try{TelephonyManager phone=dataPhone();phone.getClass().getMethod("setDataEnabled",boolean.class).invoke(phone,enable);notice(L.t("Cerere trimisă"));show();}
            catch(Exception failure){notice(L.t("Datele nu pot fi schimbate"));}
        }});
    }
    private String radio(int type){switch(type){case TelephonyManager.NETWORK_TYPE_LTE:return "4G LTE";case TelephonyManager.NETWORK_TYPE_EDGE:case TelephonyManager.NETWORK_TYPE_GPRS:case TelephonyManager.NETWORK_TYPE_GSM:return "2G";case TelephonyManager.NETWORK_TYPE_UMTS:case TelephonyManager.NETWORK_TYPE_HSDPA:case TelephonyManager.NETWORK_TYPE_HSUPA:case TelephonyManager.NETWORK_TYPE_HSPA:case TelephonyManager.NETWORK_TYPE_HSPAP:return "3G";default:return type==0?L.t("Neînregistrat"):L.t("Tip ")+type;}}
    private void details(SubscriptionInfo sim) {
        TelephonyManager phone=telephony.createForSubscriptionId(sim.getSubscriptionId());
        String operator=phone.getNetworkOperatorName();if(operator==null||operator.isEmpty())operator=L.t("Indisponibil");
        String value="Operator · "+operator+L.t("\nDate · ")+radio(phone.getDataNetworkType())+L.t("\nVoce · ")+radio(phone.getVoiceNetworkType())+"\nRoaming · "+(phone.isNetworkRoaming()?L.t("Da"):L.t("Nu"))+L.t("\n\nVoLTE și VoWiFi necesită verificări IMS separate.");
        begin("SIM "+(sim.getSimSlotIndex()+1));android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(text(value,18));root.addView(scroll,new android.widget.LinearLayout.LayoutParams(-1,0,1));back=new Runnable(){public void run(){show();}};footer("","",L.t("Înapoi"));
    }
    private void androidPage(String action){try{Intent intent=new Intent(action);intent.putExtra("android.provider.extra.SUB_ID",SubscriptionManager.getDefaultDataSubscriptionId());startActivity(intent);}catch(RuntimeException missing){notice(L.t("Pagina Android este indisponibilă"));}}
}
