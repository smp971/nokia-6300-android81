package ro.leo.visual;
import android.content.*;
/** Resume only the owner's enabled WebUI. Never provisions passwords or changes Wi-Fi. */
public final class CompanionBootReceiver extends BroadcastReceiver {
    public void onReceive(Context c,Intent i){if(i!=null&&(Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())||Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction()))&&CompanionSettings.auto(c))CompanionService.ensureStarted(c);}
}
