package ro.leo.visual;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.*;
import android.os.ResultReceiver;
import java.lang.reflect.*;

/** Shared Oreo tethering boundary. Status always comes from the Wi-Fi service. */
final class HotspotControl {
    static int state(Context c){try{WifiManager wifi=(WifiManager)c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);return (Integer)wifi.getClass().getMethod("getWifiApState").invoke(wifi);}catch(Exception unavailable){return -1;}}
    static boolean configured(Context c)throws Exception {
        WifiManager wifi=(WifiManager)c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiConfiguration config=(WifiConfiguration)wifi.getClass().getMethod("getWifiApConfiguration").invoke(wifi);
        return config!=null&&config.preSharedKey!=null&&config.preSharedKey.length()>=8&&config.allowedKeyManagement.get(4);
    }
    static void stop(Context c)throws Exception {Object manager=c.getSystemService(Context.CONNECTIVITY_SERVICE);manager.getClass().getMethod("stopTethering",int.class).invoke(manager,0);}
    static void start(Context c,ResultReceiver result)throws Exception {
        Object manager=c.getSystemService(Context.CONNECTIVITY_SERVICE);Field field=ConnectivityManager.class.getDeclaredField("mService");field.setAccessible(true);Object service=field.get(manager);
        Method start=service.getClass().getMethod("startTethering",int.class,ResultReceiver.class,boolean.class,String.class);start.setAccessible(true);start.invoke(service,0,result,true,c.getPackageName());
    }
}
