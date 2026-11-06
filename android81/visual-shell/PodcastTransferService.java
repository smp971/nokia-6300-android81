package ro.leo.visual;
import ro.leo.i18n.L;import android.app.*;import android.content.*;import android.os.*;import java.util.concurrent.*;
/** Short foreground finalizer for user-requested podcasts. Never starts a download itself. */
public final class PodcastTransferService extends Service {
 private final ExecutorService work=Executors.newSingleThreadExecutor();private PowerManager.WakeLock wake;
 public IBinder onBind(Intent i){return null;}
 public void onCreate(){super.onCreate();NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);NotificationChannel channel=new NotificationChannel("leo-podcast-copy",L.t("Podcasturi Leo"),NotificationManager.IMPORTANCE_LOW);channel.setSound(null,null);nm.createNotificationChannel(channel);startForeground(461,new Notification.Builder(this,"leo-podcast-copy").setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(L.t("Finalizez podcastul")).setContentText(L.t("Verificare și salvare în locația aleasă")).setOngoing(true).build());wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Leo:PodcastFinalize");wake.acquire(600000);}
 public int onStartCommand(Intent i,int flags,final int start){final long id=i==null?-1:i.getLongExtra("id",-1);work.execute(new Runnable(){public void run(){try{if(id>=0)PodcastDownloads.finish(PodcastTransferService.this,id);}catch(Exception ignored){}finally{stopSelfResult(start);}}});return START_NOT_STICKY;}
 public void onDestroy(){work.shutdownNow();if(wake!=null&&wake.isHeld())wake.release();stopForeground(true);super.onDestroy();}
}
