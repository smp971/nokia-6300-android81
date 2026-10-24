package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import java.util.*;

/** Optional walkthrough. Changes are made only in the individual settings pages. */
public final class SetupWizardActivity extends LeoActivity {
    private int step;
    private static final String[] TITLES=L.array(new String[]{"Limba Leo","Dată și oră","Wi-Fi","SIM-uri","Taste și scriere","Notificări","Aspect și temă","Tastatură Leo","Comenzile telefonului","Gata"});
    protected void onCreate(Bundle b){super.onCreate(b);if(b!=null)step=b.getInt("step",0);show();}
    protected void onSaveInstanceState(Bundle b){b.putInt("step",step);super.onSaveInstanceState(b);}
    protected void onResume(){super.onResume();show();}
    private void show(){String[] help={L.t("Limba Leo")+": "+Locale.getDefault().getDisplayName()+L.t("\nAlege Română sau English. Limba aplicațiilor Android se schimbă separat."),L.t("Verifică ora automată, fusul orar și formatul ceasului."),L.t("Alege rețeaua în pagina Wi-Fi. Parola se introduce din taste."),L.t("Alege separat SIM-ul pentru apeluri, SMS și date. Nu se inițiază probe de apel/SMS."),L.t("Săgeți: navigare. OK: deschide. Soft stânga: opțiuni. Soft dreapta: înapoi / șterge.\n# schimbă abc/ABC/123/T9. * schimbă sugestia T9.\nBlocare: soft stânga, apoi * pentru deblocare."),LeoNotificationService.active==null?L.t("Accesul la notificări nu este conectat. Instalatorul trebuie să activeze serviciul Leo."):L.t("Centrul nativ de notificări este conectat. Soft stânga pe Home îl deschide. Săgeată sus: configurări rapide."),L.t("Alege culorile, fundalul și mărimea textului. Schimbările se fac în pagina Personalizare."),L.t("Alege modul de scriere și pauza multi-tap. Setările actuale sunt păstrate până le modifici explicit."),L.t("Meniu fizic: meniu global, inclusiv peste aplicații Android.\nMouse: săgeți și OK; ține sus/jos la margine pentru derulare, * pentru modul manual, # pentru oprire.\nHome: 0 media, 1 căutare, 5 acces rapid."),L.t("Poți reveni oricând la acest ghid din Setări → Telefon. Preferințele existente sunt păstrate.")};
        begin(L.t("Configurare · ")+(step+1)+"/"+TITLES.length);android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(text(TITLES[step]+"\n\n"+help[step],18));root.addView(scroll,new android.widget.LinearLayout.LayoutParams(-1,0,1));
        left=new Runnable(){public void run(){open();}};back=new Runnable(){public void run(){if(step>0){step--;show();}else options(L.t("Închizi ghidul?"),new String[]{L.t("Continuă"),L.t("Închide pentru acum")},new Choice(){public void pick(int i){if(i==1)finish();}});}};
        footer(step==TITLES.length-1?L.t("Finalizează"):step==4?L.t("Blocare taste"):L.t("Configurează"),step==TITLES.length-1?L.t("Gata"):L.t("Următor"),L.t("Înapoi"));
    }
    private void complete(){getSharedPreferences("setup",0).edit().putBoolean("complete",true).apply();finish();}
    private void open(){try{if(step==0)startActivity(new Intent(this,LanguageSettingsActivity.class));else if(step==1)startActivity(new Intent(this,DateTimeSettingsActivity.class));else if(step==2)startActivity(new Intent(this,ConnectivityActivity.class).putExtra("kind","wifi"));else if(step==3)startActivity(new Intent(this,MobileSettingsActivity.class));else if(step==4)startActivity(new Intent(this,KeypadLockSettingsActivity.class));else if(step==5)startActivity(new Intent(this,NotificationsActivity.class));else if(step==6)startActivity(new Intent(this,PersonalizationActivity.class));else if(step==7)startActivity(new Intent(this,InputSettingsActivity.class));else if(step==8)startActivity(new Intent(this,HelpActivity.class).putExtra("topic",0));else complete();}catch(RuntimeException e){notice(L.t("Pagina nu este disponibilă"));}}
    public boolean dispatchKeyEvent(android.view.KeyEvent e){if(e.getKeyCode()==android.view.KeyEvent.KEYCODE_DPAD_CENTER||e.getKeyCode()==android.view.KeyEvent.KEYCODE_ENTER){if(e.getAction()==android.view.KeyEvent.ACTION_UP){if(step==TITLES.length-1)complete();else{step++;show();}}return true;}return super.dispatchKeyEvent(e);}
}
