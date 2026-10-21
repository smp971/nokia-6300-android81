package ro.leo.visual;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;

public final class AlarmRingService extends Service {
    static boolean ringing;
    private MediaPlayer player;
    private ToneGenerator tone;
    private PowerManager.WakeLock wake;
    private final Handler handler=new Handler();
    private final Runnable beep=new Runnable() { public void run() {
        if(tone!=null) { tone.startTone(ToneGenerator.TONE_PROP_BEEP,350); handler.postDelayed(this,1500); }
    }};
    public IBinder onBind(Intent intent) { return null; }
    public int onStartCommand(Intent intent,int flags,int id) {
        if(intent!=null && "stop".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        String label=intent==null?"Alarmă":intent.getStringExtra("label");
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel=new NotificationChannel("leo-alarm","Alarme Leo",NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null,null); manager.createNotificationChannel(channel);
        Intent show=new Intent(this,AlarmRingActivity.class).putExtra("label",label).putExtra("alarm_id",intent==null?-1:intent.getIntExtra("alarm_id",-1)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open=PendingIntent.getActivity(this,101,show,PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,102,new Intent(this,AlarmRingService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(310,new Notification.Builder(this,"leo-alarm").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label).setContentText("OK: oprește alarma").setContentIntent(open).setFullScreenIntent(open,true)
            .setOngoing(true).addAction(new Notification.Action.Builder(null,"Oprește",stop).build()).build());
        if(!ringing) {
            ringing=true; wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Leo:alarm"); wake.acquire(190000);
            try {
                player=new MediaPlayer(); player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                player.setDataSource(this,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)); player.setLooping(true); player.prepare(); player.start();
            } catch(Exception noFile) {
                if(player!=null) { player.release(); player=null; }
                tone=new ToneGenerator(AudioManager.STREAM_ALARM,50); handler.post(beep);
            }
            handler.postDelayed(new Runnable() { public void run() { stopSelf(); } },180000);
        }
        startActivity(show); return START_NOT_STICKY;
    }
    public void onDestroy() {
        ringing=false; handler.removeCallbacksAndMessages(null);
        if(player!=null) { player.release(); player=null; }
        if(tone!=null) { tone.release(); tone=null; }
        if(wake!=null && wake.isHeld()) wake.release(); stopForeground(true); super.onDestroy();
    }
}
