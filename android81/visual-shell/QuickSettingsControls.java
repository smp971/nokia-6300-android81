package ro.leo.visual;

import android.bluetooth.BluetoothAdapter;
import android.content.*;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import ro.leo.i18n.L;

/** Read/write settings independently of the panel's focus, geometry and drawing. */
final class QuickSettingsControls {
    static final int OFF=0,ON=1,BUSY=2,UNAVAILABLE=3;
    static final String[] TITLES=L.array(new String[]{"Wi-Fi","Bluetooth","Locație","Hotspot","Profil","Luminozitate","Media"});
    final Context context;
    QuickSettingsControls(Context c){context=c;}
    static final class State {
        final int[] toggles;final String[] labels;final int brightness,media,mediaMax;final boolean writable,mutedProfile;
        State(int[] toggles,String[] labels,int brightness,int media,int mediaMax,boolean writable,boolean mutedProfile){this.toggles=toggles;this.labels=labels;this.brightness=brightness;this.media=media;this.mediaMax=mediaMax;this.writable=writable;this.mutedProfile=mutedProfile;}
        int brightnessPercent(){return Math.round(brightness*100f/255);}
    }
    static int wifi(int state){return state==WifiManager.WIFI_STATE_ENABLED?ON:state==WifiManager.WIFI_STATE_DISABLED?OFF:state==WifiManager.WIFI_STATE_ENABLING||state==WifiManager.WIFI_STATE_DISABLING?BUSY:UNAVAILABLE;}
    static int bluetooth(int state){return state==BluetoothAdapter.STATE_ON?ON:state==BluetoothAdapter.STATE_OFF?OFF:state==BluetoothAdapter.STATE_TURNING_ON||state==BluetoothAdapter.STATE_TURNING_OFF?BUSY:UNAVAILABLE;}
    static int hotspot(int state){return state==13?ON:state==11?OFF:state==10||state==12?BUSY:UNAVAILABLE;}
    static int bounded(int value,int min,int max){return Math.max(min,Math.min(max,value));}
    State read(){
        int w=WifiManager.WIFI_STATE_UNKNOWN,b=BluetoothAdapter.ERROR,location=-1,ap=HotspotControl.state(context);
        try{w=((WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE)).getWifiState();}catch(RuntimeException ignored){}
        BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();try{if(adapter!=null)b=adapter.getState();}catch(RuntimeException ignored){}
        try{location=Settings.Secure.getInt(context.getContentResolver(),Settings.Secure.LOCATION_MODE,0);}catch(RuntimeException ignored){}
        int[] toggles={wifi(w),bluetooth(b),location<0?UNAVAILABLE:location==0?OFF:ON,hotspot(ap),ON};
        AudioManager audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);String profile=SoundProfiles.current(context);
        String[] labels={QuickSettingsActivity.wifiState(w),adapter==null?L.t("Lipsă"):QuickSettingsActivity.bluetoothState(b),L.t(location<0?"Indisponibil":location==0?"Oprită":"Pornită"),ap==13?L.t("Pornit"):ap==11?L.t("Oprit"):ap==12?L.t("Se pornește…"):ap==10?L.t("Se oprește…"):L.t("Indisponibil"),profile};
        return new State(toggles,labels,Settings.System.getInt(context.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,128),audio.getStreamVolume(AudioManager.STREAM_MUSIC),audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),Settings.System.canWrite(context),audio.getRingerMode()!=AudioManager.RINGER_MODE_NORMAL);
    }
    void toggleWifi(){WifiManager manager=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);int state=manager.getWifiState();if(wifi(state)==BUSY)throw new IllegalStateException(L.t("Așteaptă schimbarea Wi-Fi"));if(wifi(state)==UNAVAILABLE)throw new IllegalStateException(L.t("Wi-Fi indisponibil"));int ap=HotspotControl.state(context);if(state==WifiManager.WIFI_STATE_DISABLED&&(ap==13||ap==12||ap==10))throw new IllegalStateException(L.t("Oprește întâi hotspotul"));if(!manager.setWifiEnabled(state!=WifiManager.WIFI_STATE_ENABLED))throw new IllegalStateException(L.t("Wi-Fi nu acceptă schimbarea"));}
    void toggleBluetooth(){BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();if(adapter==null)throw new IllegalStateException(L.t("Bluetooth indisponibil"));int state=adapter.getState();if(bluetooth(state)==BUSY)throw new IllegalStateException(L.t("Așteaptă schimbarea Bluetooth"));if(bluetooth(state)==UNAVAILABLE||!(state==BluetoothAdapter.STATE_ON?adapter.disable():adapter.enable()))throw new IllegalStateException(L.t("Bluetooth nu acceptă schimbarea"));}
    void toggleLocation(){
        int before=Settings.Secure.getInt(context.getContentResolver(),Settings.Secure.LOCATION_MODE,0);SharedPreferences saved=context.getSharedPreferences("quick-settings",0);
        int next=before==0?bounded(saved.getInt("location-mode",Settings.Secure.LOCATION_MODE_HIGH_ACCURACY),1,3):0;
        if(!Settings.Secure.putInt(context.getContentResolver(),Settings.Secure.LOCATION_MODE,next))throw new IllegalStateException(L.t("Localizarea nu poate fi schimbată"));
        if(before>0)saved.edit().putInt("location-mode",before).apply();
    }
    void brightness(int percent){
        if(!Settings.System.canWrite(context))throw new IllegalStateException(L.t("Acces setări neacordat"));
        int value=Math.round(bounded(percent,5,100)*255f/100);
        if(!Settings.System.putInt(context.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)||!Settings.System.putInt(context.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,value))throw new IllegalStateException(L.t("Luminozitate nesalvată"));
    }
    void media(int level){AudioManager audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);audio.setStreamVolume(AudioManager.STREAM_MUSIC,bounded(level,0,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)),0);}
}
