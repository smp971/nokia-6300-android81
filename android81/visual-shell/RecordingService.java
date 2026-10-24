package ro.leo.visual;
import ro.leo.i18n.L;
import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import java.io.*;

public final class RecordingService extends Service implements AudioManager.OnAudioFocusChangeListener {
    static RecordingService instance;static String state=L.t("Oprit");static long started,pausedAt,pausedTotal;static boolean paused;
    private MediaRecorder recorder;private File partial,complete;private AudioManager audio;private boolean startedRecorder;
    public IBinder onBind(Intent intent){return null;}
    public void onCreate(){super.onCreate();instance=this;audio=(AudioManager)getSystemService(AUDIO_SERVICE);}
    public int onStartCommand(Intent intent,int flags,int id){
        String action=intent==null?"stop":intent.getAction();
        if("stop".equals(action)){finish(true);stopSelf();return START_NOT_STICKY;}
        if("pause".equals(action)){toggle();return START_NOT_STICKY;}
        if(recorder!=null)return START_NOT_STICKY;
        if(!PlaybackService.hardwareReady()){state=L.t("Driverul audio este inactiv");stopSelf();return START_NOT_STICKY;}
        if(PlaybackService.instance!=null)stopService(new Intent(this,PlaybackService.class));
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);NotificationChannel c=new NotificationChannel("leo-recorder",L.t("Reportofon Leo"),NotificationManager.IMPORTANCE_LOW);c.setSound(null,null);nm.createNotificationChannel(c);
        PendingIntent open=PendingIntent.getActivity(this,411,new Intent(this,RecorderActivity.class),PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,412,new Intent(this,RecordingService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(411,new Notification.Builder(this,"leo-recorder").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle(L.t("Înregistrare Leo")).setContentText(L.t("Microfon activ · maximum 10 minute")).setOngoing(true).setContentIntent(open).addAction(new Notification.Action.Builder(null,L.t("Oprește și salvează"),stop).build()).build());
        if(audio.requestAudioFocus(this,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){state=L.t("Microfon/sunet ocupat");stopSelf();return START_NOT_STICKY;}
        boolean folderReady=false;try{
            File dir=RecordingLocations.selected(this);folderReady=true;
            complete=new File(dir,"Leo-"+System.currentTimeMillis()+"-"+java.util.UUID.randomUUID().toString().substring(0,6)+".m4a");partial=new File(dir,complete.getName()+".part");
            recorder=new MediaRecorder();recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);recorder.setAudioSamplingRate(16000);recorder.setAudioEncodingBitRate(64000);recorder.setOutputFile(partial.getPath());recorder.setMaxDuration(600000);recorder.setMaxFileSize(64L*1024*1024);
            recorder.setOnInfoListener(new MediaRecorder.OnInfoListener(){public void onInfo(MediaRecorder r,int what,int extra){if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED||what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED){finish(true);stopSelf();}}});
            recorder.setOnErrorListener(new MediaRecorder.OnErrorListener(){public void onError(MediaRecorder r,int what,int extra){finish(false);state=L.t("Înregistrarea a eșuat");stopSelf();}});
            recorder.prepare();recorder.start();startedRecorder=true;paused=false;pausedTotal=0;started=SystemClock.elapsedRealtime();state=L.t("Înregistrare");
        }catch(Exception failure){finish(false);state=folderReady?L.t("Microfonul nu poate fi pornit"):L.t("Locația de salvare nu este disponibilă");stopSelf();}
        return START_NOT_STICKY;
    }
    private void toggle(){if(recorder==null||!startedRecorder)return;try{if(paused){recorder.resume();pausedTotal+=SystemClock.elapsedRealtime()-pausedAt;paused=false;state=L.t("Înregistrare");}else{recorder.pause();pausedAt=SystemClock.elapsedRealtime();paused=true;state=L.t("Pauză");}}catch(RuntimeException failure){finish(false);state=L.t("Pauza nu este disponibilă");stopSelf();}}
    int amplitude(){if(recorder==null||!startedRecorder||paused)return 0;try{return recorder.getMaxAmplitude();}catch(RuntimeException unavailable){return 0;}}
    static long elapsed(){return Math.max(0,(paused?pausedAt:SystemClock.elapsedRealtime())-started-pausedTotal);}
    private void finish(boolean keep){
        if(recorder!=null){try{if(startedRecorder)recorder.stop();else keep=false;}catch(RuntimeException invalid){keep=false;}recorder.release();recorder=null;startedRecorder=false;
            if(partial!=null&&partial.exists()){if(keep&&partial.length()>0&&partial.renameTo(complete))state=L.t("Salvat: ")+complete.getName();else{partial.delete();state=L.t("Înregistrare nesalvată");}}
        }
        paused=false;
    }
    public void onAudioFocusChange(int focus){if(focus<=0){finish(true);stopSelf();}}
    public void onDestroy(){finish(true);audio.abandonAudioFocus(this);stopForeground(true);instance=null;super.onDestroy();}
}
