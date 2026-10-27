package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.*;
import android.database.*;
import android.os.Handler;
import android.provider.CallLog;
import java.util.concurrent.*;

/** Leo read state. Historic SMS remain read; call acknowledgement never edits CallLog. */
final class UnreadState {
    static final String CHANGED="ro.leo.visual.UNREAD_CHANGED";
    static volatile int sms,calls;
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static boolean queued,dirty;
    private static boolean watching;
    static synchronized void watch(final Context c){if(watching)return;watching=true;final Context app=c.getApplicationContext();app.getContentResolver().registerContentObserver(CallLog.Calls.CONTENT_URI,true,new ContentObserver(new Handler(android.os.Looper.getMainLooper())){public void onChange(boolean self){refresh(app);}});refresh(app);}
    static synchronized void refresh(Context context){final Context c=context.getApplicationContext();dirty=true;if(queued)return;queued=true;worker.execute(new Runnable(){public void run(){for(;;){synchronized(UnreadState.class){dirty=false;}try{
        int s=0,n=0;try(SmsStore store=new SmsStore(c);Cursor row=store.getReadableDatabase().rawQuery("SELECT address FROM inbox WHERE unread=1",null)){while(row.moveToNext())if(!ConversationPrefs.has(c,"blocked",row.getString(0)))s++;}
        SharedPreferences p=c.getSharedPreferences("unread",0);boolean initialized=p.contains("calls_seen");long seen=p.getLong("calls_seen",0),latest=seen;
        try(Cursor row=c.getContentResolver().query(CallLog.Calls.CONTENT_URI,new String[]{CallLog.Calls.DATE},CallLog.Calls.TYPE+"=? AND "+CallLog.Calls.DATE+">?",new String[]{""+CallLog.Calls.MISSED_TYPE,""+seen},null)){while(row!=null&&row.moveToNext()){n++;latest=Math.max(latest,row.getLong(0));}}
        if(!initialized){p.edit().putLong("calls_seen",latest).commit();n=0;}sms=s;calls=n;
        c.sendBroadcast(new Intent(CHANGED).setPackage(c.getPackageName()));
        }catch(RuntimeException ignored){}synchronized(UnreadState.class){if(!dirty){queued=false;return;}}}}});}
    static void readThread(final Context c,final String address,final long through){readThread(c,address,through,null);}
    static void readThread(final Context c,final String address,final long through,final Runnable done){worker.execute(new Runnable(){public void run(){try(SmsStore store=new SmsStore(c)){ContentValues v=new ContentValues();v.put("unread",0);store.getWritableDatabase().update("inbox",v,"address=? AND date<=?",new String[]{address,""+through});try(Cursor row=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM inbox WHERE address=? AND unread=1",new String[]{address})){if(row.moveToFirst()&&row.getInt(0)==0)((android.app.NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel("leo-sms",address.hashCode());}}catch(RuntimeException ignored){}finally{refresh(c);if(done!=null)done.run();}}});}
    static void readCalls(Context c,long through){SharedPreferences p=c.getSharedPreferences("unread",0);p.edit().putLong("calls_seen",Math.max(through,p.getLong("calls_seen",0))).apply();refresh(c);}
    static String label(){int s=sms,n=calls;return s+(s==1?L.t(" SMS nou · "):L.t(" SMS noi · "))+n+(n==1?L.t(" apel pierdut"):L.t(" apeluri pierdute"));}
}
