package ro.leo.visual;
import android.content.*;
/** Charging or reboot invalidates a standby sample rather than silently misreporting it. */
public final class BatterySessionReceiver extends BroadcastReceiver {
    public void onReceive(Context c,Intent i){if(Intent.ACTION_POWER_CONNECTED.equals(i.getAction())||Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()))BatterySession.interrupt(c);}
}
