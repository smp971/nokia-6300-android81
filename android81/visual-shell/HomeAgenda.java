package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.*;import android.database.Cursor;import java.util.*;
/** Rate-limited provider work off the Home rendering thread, including recurring instances. */
final class HomeAgenda {
 static volatile int pending;static volatile String title="",when="";static volatile long start;private static long checked;private static boolean queued;
 static synchronized void refresh(final Context context){long tick=android.os.SystemClock.elapsedRealtime();if(queued||checked>0&&tick-checked<60000)return;queued=true;checked=tick;final Context c=context.getApplicationContext();CalendarReminders.worker.execute(new Runnable(){public void run(){String name="",date="";long begin=0;try{long now=System.currentTimeMillis(),own=CalendarReminders.calendar(c);if(own>=0)try(Cursor r=c.getContentResolver().query(CalendarReminders.instances(now,now+366L*86400000),new String[]{"title","begin","end"},"calendar_id=? AND end>?",new String[]{""+own,""+now},"begin ASC")){if(r!=null&&r.moveToFirst()){name=r.getString(0);begin=r.getLong(1);date=new java.text.SimpleDateFormat("EEE dd.MM HH:mm",L.locale()).format(new Date(begin));}}}catch(RuntimeException e){date=L.t("Calendar indisponibil");}pending=DailyAgendaActivity.pendingCount(c);title=name==null?"":name;when=date;start=begin;synchronized(HomeAgenda.class){queued=false;}c.sendBroadcast(new Intent(UnreadState.CHANGED).setPackage(c.getPackageName()));}});}
 static synchronized void invalidate(){checked=0;}
}
