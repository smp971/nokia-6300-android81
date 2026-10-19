package ro.leo.visual;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import java.util.*;

/** Optional walkthrough. Changes are made only in the individual settings pages. */
public final class SetupWizardActivity extends LeoActivity {
    private int step;
    private static final String[] TITLES={"Limba sistemului","Dată și oră","Wi-Fi","SIM-uri","Taste și scriere","Notificări","Gata"};
    protected void onCreate(Bundle b){super.onCreate(b);if(b!=null)step=b.getInt("step",0);show();}
    protected void onSaveInstanceState(Bundle b){b.putInt("step",step);super.onSaveInstanceState(b);}
    protected void onResume(){super.onResume();show();}
    private void show(){String[] help={"Limba Android: "+Locale.getDefault().getDisplayName()+"\nInterfața Leo este în română. Pagina Android schimbă limba aplicațiilor care au traduceri.","Verifică ora automată, fusul orar și formatul ceasului.","Alege rețeaua în pagina Wi-Fi. Parola se introduce din taste.","Alege separat SIM-ul pentru apeluri, SMS și date. Nu se inițiază probe de apel/SMS.","Săgeți: navigare. OK: deschide. Soft stânga: opțiuni. Soft dreapta: înapoi / șterge.\n# schimbă abc/ABC/123/T9. * schimbă sugestia T9.\nBlocare: soft stânga, apoi * pentru deblocare.",LeoNotificationService.active==null?"Accesul la notificări nu este conectat. Instalatorul trebuie să activeze serviciul Leo.":"Centrul nativ de notificări este conectat. Soft stânga pe Home îl deschide. Săgeată sus: configurări rapide.","Poți reveni oricând la acest ghid din Setări → Telefon. Preferințele existente sunt păstrate."};
        begin("Configurare · "+(step+1)+"/7");android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(text(TITLES[step]+"\n\n"+help[step],18));root.addView(scroll,new android.widget.LinearLayout.LayoutParams(-1,0,1));
        left=new Runnable(){public void run(){open();}};back=new Runnable(){public void run(){if(step>0){step--;show();}else options("Închizi ghidul?",new String[]{"Continuă","Închide pentru acum"},new Choice(){public void pick(int i){if(i==1)finish();}});}};
        footer(step==6?"Finalizează":step==4?"Blocare taste":"Configurează",step==6?"Gata":"Următor","Înapoi");
    }
    private void complete(){getSharedPreferences("setup",0).edit().putBoolean("complete",true).apply();finish();}
    private void open(){try{if(step==0)startActivity(new Intent(Settings.ACTION_LOCALE_SETTINGS));else if(step==1)startActivity(new Intent(this,DateTimeSettingsActivity.class));else if(step==2)startActivity(new Intent(this,ConnectivityActivity.class).putExtra("kind","wifi"));else if(step==3)startActivity(new Intent(this,MobileSettingsActivity.class));else if(step==4)startActivity(new Intent(this,KeypadLockSettingsActivity.class));else if(step==5)startActivity(new Intent(this,NotificationsActivity.class));else complete();}catch(RuntimeException e){notice("Pagina nu este disponibilă");}}
    public boolean dispatchKeyEvent(android.view.KeyEvent e){if(e.getKeyCode()==android.view.KeyEvent.KEYCODE_DPAD_CENTER||e.getKeyCode()==android.view.KeyEvent.KEYCODE_ENTER){if(e.getAction()==android.view.KeyEvent.ACTION_UP){if(step==6)complete();else{step++;show();}}return true;}return super.dispatchKeyEvent(e);}
}
