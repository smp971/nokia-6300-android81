package ro.leo.visual;

import android.os.Bundle;
import java.util.Arrays;

/** Every action is reachable with DPAD; opening the page never changes power. */
public final class PowerMenuActivity extends LeoActivity {
    static boolean visible;
    protected void onCreate(Bundle b){super.onCreate(b);show();}
    protected void onResume(){super.onResume();visible=true;}
    protected void onPause(){visible=false;super.onPause();}
    private void show(){list("Pornire și oprire",Arrays.asList("Continuă\nRevino la telefon","Repornire\nRepornește sistemul Leo","Oprire\nÎnchide telefonul"),0,new Choice(){public void pick(int i){if(i==0)finish();else confirm(i==1);}},"",null);}
    private void confirm(final boolean reboot){options(reboot?"Repornești telefonul?":"Oprești telefonul?",new String[]{"Anulează",reboot?"Da, repornește":"Da, oprește"},new Choice(){public void pick(int i){if(i==1)execute(reboot);}});}
    private void execute(final boolean reboot){notice(reboot?"Se repornește…":"Se oprește…");worker.execute(new Runnable(){public void run(){try{Process p=new ProcessBuilder("/system/bin/leo-su","-c","setprop sys.powerctl "+(reboot?"reboot":"shutdown")).start();if(!p.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)||p.exitValue()!=0)throw new java.io.IOException();}catch(Exception e){ui(new Runnable(){public void run(){notice("Comanda de oprire nu a putut fi executată");}});}}});}
}
