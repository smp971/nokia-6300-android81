package ro.leo.visual;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.*;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.Settings;
import java.util.*;

public final class QuickSettingsActivity extends LeoActivity {
    private final BroadcastReceiver updates=new BroadcastReceiver(){public void onReceive(Context c,Intent i){show();}};
    protected void onCreate(Bundle b){super.onCreate(b);}
    protected void onResume(){super.onResume();IntentFilter f=new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);f.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);registerReceiver(updates,f);show();}
    protected void onPause(){unregisterReceiver(updates);super.onPause();}
    private void show(){
        final WifiManager wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);final BluetoothAdapter bt=BluetoothAdapter.getDefaultAdapter();
        int w=wifi.getWifiState(),b=bt==null?BluetoothAdapter.ERROR:bt.getState();
        String ws=w==WifiManager.WIFI_STATE_ENABLED?"Pornit":w==WifiManager.WIFI_STATE_DISABLED?"Oprit":"În schimbare";
        String bs=b==BluetoothAdapter.STATE_ON?"Pornit":b==BluetoothAdapter.STATE_OFF?"Oprit":bt==null?"Lipsă":"În schimbare";
        AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);int r=audio.getRingerMode();
        int brightness=Settings.System.getInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,128);
        list("Configurări rapide",Arrays.asList("Wi-Fi · "+ws,"Bluetooth · "+bs,"Hotspot Wi-Fi","Sunet · "+(r==2?"Normal":r==1?"Vibrații":"Silențios"),"Luminozitate · "+Math.round(brightness*100f/255)+"%","SIM-uri și date mobile"),0,new Choice(){public void pick(int i){
            if(i==0){int state=wifi.getWifiState();if(state!=WifiManager.WIFI_STATE_ENABLED&&state!=WifiManager.WIFI_STATE_DISABLED){notice("Așteaptă schimbarea Wi-Fi");return;}try{if(!wifi.setWifiEnabled(state!=WifiManager.WIFI_STATE_ENABLED))notice("Wi-Fi nu acceptă schimbarea");}catch(RuntimeException e){notice("Wi-Fi indisponibil");}}
            else if(i==1){if(bt==null)return;int state=bt.getState();if(state!=BluetoothAdapter.STATE_ON&&state!=BluetoothAdapter.STATE_OFF){notice("Așteaptă schimbarea Bluetooth");return;}try{if(!(state==BluetoothAdapter.STATE_ON?bt.disable():bt.enable()))notice("Bluetooth nu acceptă schimbarea");}catch(RuntimeException e){notice("Bluetooth indisponibil");}}
            else if(i==2)startActivity(new Intent(QuickSettingsActivity.this,ConnectivityActivity.class).putExtra("kind","hotspot"));
            else if(i==3)profiles();else if(i==4)brightness();else startActivity(new Intent(QuickSettingsActivity.this,MobileSettingsActivity.class));
        }},"Detalii Wi-Fi",new Runnable(){public void run(){startActivity(new Intent(QuickSettingsActivity.this,ConnectivityActivity.class).putExtra("kind","wifi"));}});
    }
    private void profiles(){options("Profil",new String[]{"Normal","Vibrații","Silențios"},new Choice(){public void pick(int i){if(i==1&&!((Vibrator)getSystemService(VIBRATOR_SERVICE)).hasVibrator()){notice("Vibrator nedetectat");return;}if(!((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).isNotificationPolicyAccessGranted()){notice("Acces Nu deranjați neacordat");return;}try{((AudioManager)getSystemService(AUDIO_SERVICE)).setRingerMode(new int[]{2,1,0}[i]);show();}catch(RuntimeException e){notice("Profil nesalvat");}}});}
    private void brightness(){options("Luminozitate",new String[]{"25%","50%","75%","100%"},new Choice(){public void pick(int i){try{if(!Settings.System.canWrite(QuickSettingsActivity.this)){notice("Acces setări neacordat");return;}Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,0);Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,new int[]{64,128,192,255}[i]);show();}catch(RuntimeException e){notice("Luminozitate nesalvată");}}});}
}
