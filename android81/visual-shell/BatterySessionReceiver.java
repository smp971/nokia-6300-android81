package ro.leo.visual;
import android.content.*;
/** Charging or reboot invalidates a standby sample rather than silently misreporting it. */
public final class BatterySessionReceiver extends BroadcastReceiver {
    public void onReceive(Context c,Intent i){if(Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()))BatterySession.interrupt(c);else if(Intent.ACTION_POWER_CONNECTED.equals(i.getAction())){Intent b=c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));if(b!=null)BatterySession.observe(c,b);}}
}
