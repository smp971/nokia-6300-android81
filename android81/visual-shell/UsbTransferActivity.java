package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.Context;
import android.os.*;
import android.telecom.TelecomManager;
import java.util.Arrays;

/** User-controlled MTP on this engineering build, preserving diagnostic ADB. */
public final class UsbTransferActivity extends LeoActivity {
    private final Handler handler=new Handler();private String seen="";
    private boolean pending;private long requestedAt;private String requestedState;
    private final Runnable refresh=new Runnable(){public void run(){
        String value=property("sys.leo.usb.state","adb");
        if(pending&&(value.equals(requestedState)||"failed".equals(value))){pending=false;show();}
        if(pending&&SystemClock.uptimeMillis()-requestedAt>15000){pending=false;notice(L.t("Comanda USB nu a fost confirmată"));show();}
        if(!value.equals(seen))show();handler.postDelayed(this,1000);
    }};
    private static String property(String key,String fallback){try{return (String)Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class).invoke(null,key,fallback);}catch(Exception unavailable){return fallback;}}
    protected void onCreate(Bundle state){super.onCreate(state);}
    protected void onResume(){super.onResume();show();handler.removeCallbacks(refresh);handler.post(refresh);}
    protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    private void show(){
        seen=property("sys.leo.usb.state","adb");final boolean enabled=seen.startsWith("mtp");
        String status=pending||"switching".equals(seen)?L.t("Se schimbă…"):"failed".equals(seen)?L.t("Eroare"):enabled?L.t("Pornit"):L.t("Oprit");
        list("Transfer USB",Arrays.asList(L.t("Transfer fișiere · ")+status,L.t("Memorie internă și microSD"),L.t("Cum copiez fișiere?")),0,new Choice(){public void pick(int i){
            if(i==0)toggle(enabled);else if(i==1)notice(L.t("Ambele memorii apar pe PC când transferul este pornit"));
            else notice(L.t("Conectează USB, pornește transferul și deschide Nokia în Explorer. Oprește după terminarea copierii."));
        }},L.t("Actualizează"),new Runnable(){public void run(){show();}});
    }
    private void toggle(boolean enabled){
        if(pending||"switching".equals(seen))return;
        TelecomManager calls=(TelecomManager)getSystemService(Context.TELECOM_SERVICE);
        try{if(calls!=null&&calls.isInCall()){notice(L.t("Închide apelul înainte de schimbarea modului USB"));return;}}catch(SecurityException denied){notice(L.t("Starea apelului nu poate fi verificată"));return;}
        try{
            Class.forName("android.os.SystemProperties").getMethod("set",String.class,String.class).invoke(null,"sys.leo.usb.request",enabled?"adb":"mtp");
            requestedState=enabled?"adb":"mtp,adb";pending=true;requestedAt=SystemClock.uptimeMillis();show();
        }catch(Exception denied){notice(L.t("Transferul USB nu poate fi schimbat"));}
    }
}
