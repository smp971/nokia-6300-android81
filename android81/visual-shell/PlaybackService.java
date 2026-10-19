package ro.leo.visual;
import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import java.io.File;

/** User-started playback. Never loads kernel modules or restarts native audio. */
public final class PlaybackService extends Service implements AudioManager.OnAudioFocusChangeListener {
    static PlaybackService instance;static String state="Oprit",title="";
    private MediaPlayer player; private AudioManager audio;private boolean ready;
    public IBinder onBind(Intent intent){return null;}
    static boolean hardwareReady(){return new File("/proc/asound/card0").exists();}
    public void onCreate(){super.onCreate();instance=this;audio=(AudioManager)getSystemService(AUDIO_SERVICE);}
    public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null||"stop".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if("toggle".equals(intent.getAction())){toggle();return START_NOT_STICKY;}
        if(!hardwareReady()){state="Driverul audio este inactiv";stopSelf();return START_NOT_STICKY;}
        release();title=intent.getStringExtra("title");if(title==null)title="Muzică Leo";
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);NotificationChannel channel=new NotificationChannel("leo-music","Muzică Leo",NotificationManager.IMPORTANCE_LOW);channel.setSound(null,null);nm.createNotificationChannel(channel);
        PendingIntent open=PendingIntent.getActivity(this,401,new Intent(this,MediaActivity.class).putExtra("kind","audio"),PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,402,new Intent(this,PlaybackService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(401,new Notification.Builder(this,"leo-music").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title).setContentText("Redare Leo").setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Oprește",stop).build()).build());
        if(audio.requestAudioFocus(this,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){state="Sunet ocupat de altă aplicație";stopSelf();return START_NOT_STICKY;}
        try{
            state="Se pregătește…";player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setDataSource(this,intent.getData());player.setOnPreparedListener(new MediaPlayer.OnPreparedListener(){public void onPrepared(MediaPlayer p){ready=true;p.start();state="Redare";}});
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){public void onCompletion(MediaPlayer p){state="Terminat";stopSelf();}});
            player.setOnErrorListener(new MediaPlayer.OnErrorListener(){public boolean onError(MediaPlayer p,int what,int extra){state="Fișierul nu poate fi redat";stopSelf();return true;}});player.prepareAsync();
        }catch(Exception failure){state="Redarea a eșuat";stopSelf();}
        return START_NOT_STICKY;
    }
    void toggle(){if(!ready||player==null)return;try{if(player.isPlaying()){player.pause();state="Pauză";}else if(audio.requestAudioFocus(this,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED){player.start();state="Redare";}}catch(IllegalStateException ignored){}}
    void seek(int delta){if(ready&&player!=null)try{player.seekTo(Math.max(0,Math.min(player.getDuration(),player.getCurrentPosition()+delta)));}catch(IllegalStateException ignored){}}
    int position(){try{return ready&&player!=null?player.getCurrentPosition():0;}catch(IllegalStateException ignored){return 0;}}
    int duration(){try{return ready&&player!=null?player.getDuration():0;}catch(IllegalStateException ignored){return 0;}}
    public void onAudioFocusChange(int focus){if(focus<=0&&ready&&player!=null)try{player.pause();state="Pauză · alt sunet";}catch(IllegalStateException ignored){}}
    private void release(){ready=false;if(player!=null){player.release();player=null;}}
    public void onDestroy(){release();audio.abandonAudioFocus(this);stopForeground(true);if("Redare".equals(state)||state.startsWith("Pauză")||state.startsWith("Se pregătește"))state="Oprit";instance=null;super.onDestroy();}
}
