package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.*;
import android.os.*;
import android.provider.*;
import android.telephony.*;
import java.util.*;
/** Honest SMS/MMS readiness view: never sends or downloads a message. */
public final class MessageTransportActivity extends LeoActivity {
    protected void onCreate(Bundle b){super.onCreate(b);show();}
    private void show(){
        int sub=SubscriptionManager.getDefaultDataSubscriptionId();SubscriptionInfo sim=SubscriptionManager.from(this).getActiveSubscriptionInfo(sub);
        boolean service=false;try{service=getPackageManager().getApplicationInfo("com.android.mms.service",0).enabled;}catch(Exception ignored){}
        PersistableBundle config=null;
        try{CarrierConfigManager carrier=(CarrierConfigManager)getSystemService(CARRIER_CONFIG_SERVICE);if(carrier!=null)config=carrier.getConfigForSubId(sub);}catch(RuntimeException ignored){}
        String sms=Telephony.Sms.getDefaultSmsPackage(this);
        ArrayList<String> rows=new ArrayList<String>();
        rows.add(L.t("SIM pentru date · ")+(sim==null?L.t("Neales"):"SIM "+(sim.getSimSlotIndex()+1)));
        rows.add(L.t("Serviciu MMS Android · ")+(service?L.t("Prezent"):L.t("Indisponibil")));
        rows.add(L.t("MMS permis de configurație · ")+(config==null?L.t("Necunoscut"):config.getBoolean("enabledMMS",true)?L.t("Da"):L.t("Nu")));
        rows.add(L.t("Aplicație SMS implicită · ")+(sms==null?L.t("Nealeasă"):sms));
        rows.add(L.t("Leo: SMS funcțional; MMS încă neimplementat"));
        rows.add(L.t("Deschide APN pentru SIM-ul de date"));
        list(L.t("SMS și MMS"),rows,0,new Choice(){public void pick(int n){if(n==5)try{startActivity(new Intent(Settings.ACTION_APN_SETTINGS).putExtra("android.provider.extra.SUB_ID",SubscriptionManager.getDefaultDataSubscriptionId()));}catch(RuntimeException e){notice(L.t("Pagina Android este indisponibilă"));}}},L.t("Actualizează"),new Runnable(){public void run(){show();}});
    }
}
