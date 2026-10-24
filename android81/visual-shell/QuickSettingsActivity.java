package ro.leo.visual;
import ro.leo.i18n.L;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.*;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.Settings;
import java.util.*;

public final class QuickSettingsActivity extends LeoActivity {
    private boolean volumePage;private int quickSelected,volumeSelected;
    private final int[] streams={AudioManager.STREAM_MUSIC,AudioManager.STREAM_RING,AudioManager.STREAM_ALARM,AudioManager.STREAM_VOICE_CALL};
    private final BroadcastReceiver updates=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(volumePage)volumes(volumeSelected);else show();}};
    protected void onCreate(Bundle b){super.onCreate(b);}
    protected void onResume(){super.onResume();IntentFilter f=new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);f.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);registerReceiver(updates,f);if(volumePage||getIntent().getBooleanExtra("volumes",false))volumes(volumeSelected);else show();}
    protected void onPause(){unregisterReceiver(updates);super.onPause();}
    private void show(){
        volumePage=false;
        final WifiManager wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);final BluetoothAdapter bt=BluetoothAdapter.getDefaultAdapter();
        int w=wifi.getWifiState(),b=bt==null?BluetoothAdapter.ERROR:bt.getState();
        String ws=w==WifiManager.WIFI_STATE_ENABLED?L.t("Pornit"):w==WifiManager.WIFI_STATE_DISABLED?L.t("Oprit"):L.t("În schimbare");
        String bs=b==BluetoothAdapter.STATE_ON?L.t("Pornit"):b==BluetoothAdapter.STATE_OFF?L.t("Oprit"):bt==null?L.t("Lipsă"):L.t("În schimbare");
        AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);int r=audio.getRingerMode();
        int brightness=Settings.System.getInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,128);
        list(L.t("Configurări rapide"),Arrays.asList("Wi-Fi · "+ws,"Bluetooth · "+bs,"Hotspot Wi-Fi",L.t("Sunet · ")+SoundProfiles.current(this),L.t("Luminozitate · ")+Math.round(brightness*100f/255)+"%",L.t("SIM-uri și date mobile"),L.t("Volum · muzică, sonerie, alarmă, apel")),quickSelected,new Choice(){public void pick(int i){
            if(i==0){int state=wifi.getWifiState();if(state!=WifiManager.WIFI_STATE_ENABLED&&state!=WifiManager.WIFI_STATE_DISABLED){notice(L.t("Așteaptă schimbarea Wi-Fi"));return;}try{if(!wifi.setWifiEnabled(state!=WifiManager.WIFI_STATE_ENABLED))notice(L.t("Wi-Fi nu acceptă schimbarea"));}catch(RuntimeException e){notice(L.t("Wi-Fi indisponibil"));}}
            else if(i==1){if(bt==null)return;int state=bt.getState();if(state!=BluetoothAdapter.STATE_ON&&state!=BluetoothAdapter.STATE_OFF){notice(L.t("Așteaptă schimbarea Bluetooth"));return;}try{if(!(state==BluetoothAdapter.STATE_ON?bt.disable():bt.enable()))notice(L.t("Bluetooth nu acceptă schimbarea"));}catch(RuntimeException e){notice(L.t("Bluetooth indisponibil"));}}
            else if(i==2)startActivity(new Intent(QuickSettingsActivity.this,ConnectivityActivity.class).putExtra("kind","hotspot"));
            else if(i==3)profiles();else if(i==4)brightness();else if(i==5)startActivity(new Intent(QuickSettingsActivity.this,MobileSettingsActivity.class));else volumes(0);
        }},L.t("Detalii Wi-Fi"),new Runnable(){public void run(){startActivity(new Intent(QuickSettingsActivity.this,ConnectivityActivity.class).putExtra("kind","wifi"));}});
    }
    android.graphics.drawable.Drawable rowIcon(String title,int index){return new QuickIcon(title.startsWith(L.t("Volum"))?6:index,dp(29));}
    void onListSelection(String title,int index){if(title.startsWith(L.t("Volum")))volumeSelected=index;else if(title.equals(L.t("Configurări rapide")))quickSelected=index;}
    private void volumes(int selected){volumePage=true;volumeSelected=selected;final AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);String[] names={L.t("Muzică"),L.t("Sonerie"),L.t("Alarmă"),L.t("Apel")};ArrayList<String> labels=new ArrayList<String>();for(int i=0;i<streams.length;i++)labels.add(names[i]+" · "+audio.getStreamVolume(streams[i])+" / "+audio.getStreamMaxVolume(streams[i]));if(replaceRows(L.t("Volum · ← / → nivel"),labels))return;list(L.t("Volum · ← / → nivel"),labels,selected,new Choice(){public void pick(final int slot){int max=audio.getStreamMaxVolume(streams[slot]);String[] levels=new String[max+1];for(int n=0;n<=max;n++)levels[n]=L.t("Nivel ")+n+" / "+max;options(L.t("Volum ")+new String[]{L.t("muzică"),L.t("sonerie"),L.t("alarmă"),L.t("apel")}[slot],levels,new Choice(){public void pick(int value){try{audio.setStreamVolume(streams[slot],value,0);}catch(RuntimeException e){notice(L.t("Nivelul nu poate fi schimbat"));}volumes(slot);}});}},L.t("Înapoi"),new Runnable(){public void run(){show();}});back=new Runnable(){public void run(){show();}};}
    public boolean onKeyDown(int key,android.view.KeyEvent e){if(volumePage&&(key==android.view.KeyEvent.KEYCODE_DPAD_LEFT||key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT)){try{((AudioManager)getSystemService(AUDIO_SERVICE)).adjustStreamVolume(streams[volumeSelected],key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT?1:-1,0);}catch(RuntimeException error){notice(L.t("Volumul nu poate fi schimbat"));}volumes(volumeSelected);return true;}return super.onKeyDown(key,e);}
    private void profiles(){SoundProfiles.select(this,new Runnable(){public void run(){show();}});}

    private void brightness(){options(L.t("Luminozitate"),new String[]{"25%","50%","75%","100%"},new Choice(){public void pick(int i){try{if(!Settings.System.canWrite(QuickSettingsActivity.this)){notice(L.t("Acces setări neacordat"));return;}Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,0);Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,new int[]{64,128,192,255}[i]);show();}catch(RuntimeException e){notice(L.t("Luminozitate nesalvată"));}}});}
}
