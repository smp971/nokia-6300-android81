package ro.leo.visual;
import android.content.*;import android.os.SystemClock;
public final class MusicSleepReceiver extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){long due=c.getSharedPreferences("music-sleep",0).getLong("due",0);if(!"ro.leo.visual.MUSIC_SLEEP".equals(i.getAction())||due<=0||SystemClock.elapsedRealtime()<due)return;MusicSleep.cancel(c);c.stopService(new Intent(c,PlaybackService.class));}
}
