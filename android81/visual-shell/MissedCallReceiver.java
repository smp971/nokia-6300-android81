package ro.leo.visual;
import android.content.*;import android.telecom.TelecomManager;
/** Receives Telecom's protected default-dialer missed-call update, without logging identities. */
public final class MissedCallReceiver extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){if(TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION.equals(i.getAction())){CallNotices.missed(c,Math.max(0,i.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT,0)));UnreadState.refresh(c);}}
}
