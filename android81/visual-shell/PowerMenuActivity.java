package ro.leo.visual;
import ro.leo.i18n.L;

import android.os.Bundle;
import java.util.Arrays;

/** Every action is reachable with DPAD; opening the page never changes power. */
public final class PowerMenuActivity extends LeoActivity {
    static boolean visible;
    protected void onCreate(Bundle b){super.onCreate(b);show();}
    protected void onResume(){super.onResume();visible=true;}
    protected void onPause(){visible=false;super.onPause();}
    private void show(){list(L.t("Pornire și oprire"),Arrays.asList(L.t("Continuă\nRevino la telefon"),L.t("Repornire\nRepornește sistemul Leo"),L.t("Oprire\nÎnchide telefonul")),0,new Choice(){public void pick(int i){if(i==0)finish();else confirm(i==1);}},"",null);}
    private void confirm(final boolean reboot){options(reboot?L.t("Repornești telefonul?"):L.t("Oprești telefonul?"),new String[]{L.t("Anulează"),reboot?L.t("Da, repornește"):L.t("Da, oprește")},new Choice(){public void pick(int i){if(i==1)execute(reboot);}});}
    private void execute(final boolean reboot){notice(reboot?L.t("Se repornește…"):L.t("Se oprește…"));worker.execute(new Runnable(){public void run(){try{Process p=new ProcessBuilder("/system/bin/leo-su","-c","setprop sys.powerctl "+(reboot?"reboot":"shutdown")).start();if(!p.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)||p.exitValue()!=0)throw new java.io.IOException();}catch(Exception e){ui(new Runnable(){public void run(){notice(L.t("Comanda de oprire nu a putut fi executată"));}});}}});}
}
