package ro.leo.visual;
import android.app.*;import android.content.*;import android.os.*;
/** One explicit monotonic sleep deadline; cancelled when the player service ends. */
final class MusicSleep {
 static PendingIntent pending(Context c){return PendingIntent.getBroadcast(c,406,new Intent(c,MusicSleepReceiver.class).setAction("ro.leo.visual.MUSIC_SLEEP"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 static void set(Context c,int minutes){if(minutes<1||minutes>120)throw new IllegalArgumentException();long due=SystemClock.elapsedRealtime()+minutes*60000L;c.getSharedPreferences("music-sleep",0).edit().putLong("due",due).commit();((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,due,pending(c));}
 static void cancel(Context c){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(pending(c));c.getSharedPreferences("music-sleep",0).edit().remove("due").commit();}
 static long remaining(Context c){return Math.max(0,c.getSharedPreferences("music-sleep",0).getLong("due",0)-SystemClock.elapsedRealtime());}
}
