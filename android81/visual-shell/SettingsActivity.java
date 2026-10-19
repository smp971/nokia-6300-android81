package ro.leo.visual;
import android.app.NotificationManager;
import android.content.*;
import android.media.AudioManager;
import android.os.*;
import android.provider.Settings;
import java.util.*;

public final class SettingsActivity extends LeoActivity {
    private static final String[] TABS={"Rețele","Aspect","Siguranță","Telefon"};
    private final int[] selections=new int[4];
    private int category;
    private boolean categories;
    protected void onCreate(Bundle state) { super.onCreate(state); }
    protected void onResume() { super.onResume(); show(); }
    private void rememberSelection() {
        if(categories && root!=null) for(int i=0;i<root.getChildCount();i++)
            if(root.getChildAt(i) instanceof android.widget.ListView) {
                int position=((android.widget.ListView)root.getChildAt(i)).getSelectedItemPosition();
                if(position>=0) selections[category]=position;
            }
    }
    private void show() {
        rememberSelection(); categories=true;
        AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        String profile=audio.getRingerMode()==AudioManager.RINGER_MODE_SILENT?"Silențios":audio.getRingerMode()==AudioManager.RINGER_MODE_VIBRATE?"Vibrații":"Normal";
        String[][] rows={{"Wi-Fi","Bluetooth","Hotspot Wi-Fi","SIM-uri și rețea mobilă"},
            {"Profil · "+profile,"Luminozitate","Stingerea ecranului","Fundaluri și teme","Tastatură și T9"},
            {"Blocarea tastelor","Permisiuni și aplicații","Localizare"},
            {"Dată și oră","Stocare","Despre telefon","Setări Android","Configurare inițială"}};
        list("Setări Leo",Arrays.asList(rows[category]),selections[category],new Choice(){public void pick(int i){
            selections[category]=i;
            if(category==0) {
                if(i<3) startActivity(new Intent(SettingsActivity.this,ConnectivityActivity.class).putExtra("kind",new String[]{"wifi","bluetooth","hotspot"}[i]));
                else startActivity(new Intent(SettingsActivity.this,MobileSettingsActivity.class));
            } else if(category==1) {
                if(i==0) profiles(); else if(i==1) brightness(); else if(i==2) timeout();
                else if(i==3) startActivity(new Intent(SettingsActivity.this,WallpaperActivity.class));
                else page("Tastatură Leo","# schimbă abc / ABC / 123 / T9 / T9 ABC.\nT9: o apăsare pentru fiecare literă, * schimbă sugestia, 0 acceptă și adaugă spațiu.\nDicționar românesc local de bază; fără învățare automată.");
            } else if(category==2) {
                startActivity(new Intent(SettingsActivity.this,i==0?KeypadLockSettingsActivity.class:i==1?AppSettingsActivity.class:PrivacySettingsActivity.class));
            } else {
                if(i==0) startActivity(new Intent(SettingsActivity.this,DateTimeSettingsActivity.class)); else if(i==1) storage();
                else if(i==2) page("Despre Leo",Build.MODEL+"\nAndroid "+Build.VERSION.RELEASE+" · API "+Build.VERSION.SDK_INT+"\nShell nativ pentru taste\n"+Build.DISPLAY);
                else if(i==3)launch(Settings.ACTION_SETTINGS);else startActivity(new Intent(SettingsActivity.this,SetupWizardActivity.class));
            }
        }},"Categorii",new Runnable(){public void run(){options("Categorii",TABS,new Choice(){public void pick(int i){switchCategory(i);}});}});
        android.widget.LinearLayout tabs=new android.widget.LinearLayout(this);
        tabs.setBackgroundColor(0xff100f1d);
        for(int i=0;i<TABS.length;i++) {
            final int index=i;
            android.widget.TextView tab=text(TABS[i],12); tab.setPadding(0,0,0,0);tab.setGravity(android.view.Gravity.CENTER);
            tab.setTextColor(i==category?0xff38224f:0xffdfcde9);tab.setBackgroundColor(i==category?0xfff5eefb:0xff100f1d);
            tab.setOnClickListener(new android.view.View.OnClickListener(){public void onClick(android.view.View v){switchCategory(index);}});
            tabs.addView(tab,new android.widget.LinearLayout.LayoutParams(0,dp(32),1));
        }
        root.addView(tabs,1);
    }
    private void switchCategory(int next) {
        rememberSelection(); categories=false; category=(next+TABS.length)%TABS.length; show();
    }
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        int key=event.getKeyCode();
        if(categories && (key==android.view.KeyEvent.KEYCODE_DPAD_LEFT || key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT)) {
            if(event.getAction()==android.view.KeyEvent.ACTION_DOWN && event.getRepeatCount()==0)
                switchCategory(category+(key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT?1:-1));
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    private void launch(String action) { try { startActivity(new Intent(action)); } catch(RuntimeException unavailable) { notice("Pagina Android nu este disponibilă"); } }
    private boolean writable() {
        if(Settings.System.canWrite(this)) return true;
        notice("Permite modificarea setărilor pentru Leo Visual");
        try { startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,android.net.Uri.parse("package:"+getPackageName()))); } catch(RuntimeException ignored) { }
        return false;
    }
    private void brightness() { options("Luminozitate",new String[]{"25%","50%","75%","100%"},new Choice() { public void pick(int i) {
        if(!writable()) return; try { Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,new int[]{64,128,192,255}[i]); notice("Luminozitate salvată"); } catch(SecurityException denied) { notice("Acces refuzat"); }
    }}); }
    private void timeout() { options("Stingerea ecranului",new String[]{"30 secunde","1 minut","5 minute","30 minute"},new Choice() { public void pick(int i) {
        if(!writable()) return; try { Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_OFF_TIMEOUT,new int[]{30000,60000,300000,1800000}[i]);notice("Interval salvat"); } catch(SecurityException denied) { notice("Acces refuzat"); }
    }}); }
    private void profiles() { final boolean vibration=((Vibrator)getSystemService(VIBRATOR_SERVICE)).hasVibrator();options("Profil sunet",new String[]{"Normal",vibration?"Vibrații":"Vibrații · indisponibile","Silențios"},new Choice() { public void pick(int i) {
        if(i==1&&!vibration){notice("Android nu detectează vibratorul");return;}
        NotificationManager notifications=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(!notifications.isNotificationPolicyAccessGranted()) { notice("Permite accesul Leo la Nu deranjați");launch(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);return; }
        try { ((AudioManager)getSystemService(AUDIO_SERVICE)).setRingerMode(i==0?AudioManager.RINGER_MODE_NORMAL:i==1?AudioManager.RINGER_MODE_VIBRATE:AudioManager.RINGER_MODE_SILENT);show(); } catch(SecurityException denied) { notice("Profilul nu poate fi schimbat"); }
    }}); }
    private String capacity(java.io.File dir) {
        try { StatFs stat=new StatFs(dir.getPath());return android.text.format.Formatter.formatFileSize(this,stat.getAvailableBytes())+" liber / "+android.text.format.Formatter.formatFileSize(this,stat.getTotalBytes()); } catch(RuntimeException unavailable) { return "indisponibil"; }
    }
    private void storage() { page("Stocare","Date interne\n"+capacity(Environment.getDataDirectory())+"\n\nMemorie partajată\n"+capacity(Environment.getExternalStorageDirectory())); }
    private void page(String title,String value) { rememberSelection();categories=false;begin(title);android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(text(value,18));root.addView(scroll,new android.widget.LinearLayout.LayoutParams(-1,0,1));back=new Runnable(){public void run(){show();}};footer("","","Înapoi"); }
}
