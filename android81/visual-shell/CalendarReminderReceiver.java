package ro.leo.visual;
import android.content.*;
public final class CalendarReminderReceiver extends BroadcastReceiver {
 public void onReceive(final Context c,Intent i){if(!CalendarReminders.FIRE.equals(i.getAction()))return;final PendingResult result=goAsync();CalendarReminders.worker.execute(new Runnable(){public void run(){try{CalendarReminders.fire(c);}catch(RuntimeException e){android.util.Log.w("LeoCalendar","Reminder unavailable");}finally{result.finish();}}});}
}
