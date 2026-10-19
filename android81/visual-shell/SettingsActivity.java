package ro.leo.visual;
import android.app.NotificationManager;
import android.content.*;
import android.media.AudioManager;
import android.os.*;
import android.provider.Settings;
import java.util.*;

public final class SettingsActivity extends LeoActivity {
    protected void onCreate(Bundle state) { super.onCreate(state); }
    protected void onResume() { super.onResume(); show(); }
    private void show() {
        AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        String profile=audio.getRingerMode()==AudioManager.RINGER_MODE_SILENT?"Silențios":audio.getRingerMode()==AudioManager.RINGER_MODE_VIBRATE?"Vibrații":"Normal";
        list("Setări Leo",Arrays.asList("Profil · "+profile,"Luminozitate","Stingerea ecranului","Fundaluri și teme","Tastatură și T9","Dată și oră","Stocare","Rețea mobilă","Wi-Fi · experimental","Bluetooth · experimental","Despre telefon","Setări Android"),0,new Choice() { public void pick(int i) {
            if(i==0) profiles();
            else if(i==1) brightness(); else if(i==2) timeout();
            else if(i==3) startActivity(new Intent(SettingsActivity.this,WallpaperActivity.class));
            else if(i==4) page("Tastatură Leo","# schimbă abc / ABC / 123 / T9 / T9 ABC.\nT9: o apăsare pentru fiecare literă, * schimbă sugestia, 0 acceptă și adaugă spațiu.\nDicționar românesc local de bază; fără învățare automată.");
            else if(i==5) launch(Settings.ACTION_DATE_SETTINGS);
            else if(i==6) storage(); else if(i==7) launch(Settings.ACTION_NETWORK_OPERATOR_SETTINGS);
            else if(i==8) launch(Settings.ACTION_WIFI_SETTINGS); else if(i==9) launch(Settings.ACTION_BLUETOOTH_SETTINGS);
            else if(i==10) page("Despre Leo",Build.MODEL+"\nAndroid "+Build.VERSION.RELEASE+" · API "+Build.VERSION.SDK_INT+"\nShell nativ pentru taste\n"+Build.DISPLAY);
            else launch(Settings.ACTION_SETTINGS);
        } },"",null);
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
    private void page(String title,String value) { begin(title);android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(text(value,18));root.addView(scroll,new android.widget.LinearLayout.LayoutParams(-1,0,1));back=new Runnable(){public void run(){show();}};footer("","","Înapoi"); }
}
