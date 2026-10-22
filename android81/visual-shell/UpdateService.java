package ro.leo.visual;
import android.app.*;import android.content.*;import android.os.*;
public final class UpdateService extends Service {
 private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();public IBinder onBind(Intent i){return null;}
 public int onStartCommand(Intent i,int flags,int id){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("leo-updates","Actualizări Leo",NotificationManager.IMPORTANCE_LOW));startForeground(698,new Notification.Builder(this,"leo-updates").setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Actualizare Leo").setContentText("Pregătesc sesiunea de instalare Android").build());worker.execute(new Runnable(){public void run(){try{UpdateEngine.run(UpdateService.this);}catch(Exception e){UpdateEngine.fail(UpdateService.this,"Instalare oprită: "+e.getMessage());FaultLedger.record(UpdateService.this,"Update",e);}finally{UpdateEngine.changed(UpdateService.this);stopSelf(id);}}});return START_NOT_STICKY;}
 public void onDestroy(){worker.shutdown();stopForeground(true);super.onDestroy();}
}
