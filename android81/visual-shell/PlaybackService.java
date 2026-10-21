package ro.leo.visual;
import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import java.io.File;
import android.graphics.*;import android.media.session.*;import java.util.*;import java.util.concurrent.*;

/** User-started playback. Never loads kernel modules or restarts native audio. */
public final class PlaybackService extends Service implements AudioManager.OnAudioFocusChangeListener {
    static PlaybackService instance;static String state="Oprit",title="";
    final MusicQueue queue=new MusicQueue();
    private MediaSession mediaSession;private Bitmap cover;private final Handler main=new Handler();private final ExecutorService metadata=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(1),new ThreadPoolExecutor.DiscardOldestPolicy());
    private MediaPlayer player; private AudioManager audio;private boolean ready;private int resumePosition;private boolean bookmarkReady;
    private final Runnable bookmark=new Runnable(){public void run(){saveBookmark();main.postDelayed(this,30000);}};
    private void saveBookmark(){if(bookmarkReady&&ready){MusicResume.schedule(this,queue,position());}}private WmaDecode wma;private volatile int generation;
    public IBinder onBind(Intent intent){return null;}
    static boolean hardwareReady(){return new File("/proc/asound/card0").exists();}
    public void onCreate(){super.onCreate();instance=this;MusicSleep.cancel(this);main.postDelayed(bookmark,30000);queue.shuffle=getSharedPreferences("music-player",0).getBoolean("shuffle",false);queue.repeat=Math.max(0,Math.min(2,getSharedPreferences("music-player",0).getInt("repeat",0)));audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        mediaSession=new MediaSession(this,"Leo Music");mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);mediaSession.setCallback(new MediaSession.Callback(){public void onPlay(){if(!playing())toggle();}public void onPause(){if(playing())toggle();}public void onSkipToNext(){next(true);}public void onSkipToPrevious(){next(false);}public void onStop(){stopSelf();}public void onSeekTo(long pos){seek((int)Math.max(Integer.MIN_VALUE,Math.min(Integer.MAX_VALUE,pos-position())));}});mediaSession.setActive(true);
        File[] stale=getCacheDir().listFiles();if(stale!=null)for(File f:stale)if(f.isFile()&&f.getName().startsWith("leo-wma-"))f.delete();
    }
    public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null||"stop".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if("toggle".equals(intent.getAction())){if(queue.index<0)stopSelf();else toggle();return START_NOT_STICKY;}
        if("resume".equals(intent.getAction())){if(!queue.sources.isEmpty()){if(!playing())toggle();return START_NOT_STICKY;}try{MusicResume.Session saved=MusicResume.read(this);queue.set(saved.sources,saved.titles,saved.index);resumePosition=saved.position;}catch(Exception e){state="Ultima sesiune nu este disponibilă";stopSelf();return START_NOT_STICKY;}if(!hardwareReady()){state="Driverul audio este inactiv";stopSelf();return START_NOT_STICKY;}playCurrent();return START_NOT_STICKY;}
        if(!hardwareReady()){state="Driverul audio este inactiv";stopSelf();return START_NOT_STICKY;}
        saveBookmark();bookmarkReady=false;resumePosition=0;ArrayList<String> sources=intent.getStringArrayListExtra("queue"),labels=intent.getStringArrayListExtra("titles");
        if(sources!=null&&labels!=null)queue.set(sources,labels,intent.getIntExtra("index",0));
        else if(intent.getData()!=null)queue.set(Arrays.asList(intent.getData().toString()),Arrays.asList(intent.getStringExtra("title")==null?"Muzică Leo":intent.getStringExtra("title")),0);
        playCurrent();return START_NOT_STICKY;
    }
    private void playCurrent(){
        if(queue.index<0){stopSelf();return;}saveBookmark();release();title=queue.titles.get(queue.index);
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);NotificationChannel channel=new NotificationChannel("leo-music","Muzică Leo",NotificationManager.IMPORTANCE_LOW);channel.setSound(null,null);nm.createNotificationChannel(channel);
        publish();
        if(audio.requestAudioFocus(this,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){state="Sunet ocupat de altă aplicație";stopSelf();return;}
        final Uri source=Uri.parse(queue.sources.get(queue.index));
        if(source==null){state="Fișier indisponibil";stopSelf();return;}
        loadCover(source);String type=null;try{type=getContentResolver().getType(source);}catch(RuntimeException ignored){}
        if(title.toLowerCase(java.util.Locale.ROOT).endsWith(".wma")||"audio/x-ms-wma".equals(type)){
            state="Se pregătește WMA…";final int request=generation;wma=new WmaDecode();
            wma.start(this,source,new WmaDecode.Callback(){public void complete(File wave,String error){
                if(request!=generation)return;
                if(error!=null){state=error;stopSelf();}else prepare(Uri.fromFile(wave));
            }});
        }else prepare(source);
    }
    private void prepare(Uri source){
        try{
            state="Se pregătește…";player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setDataSource(this,source);player.setOnPreparedListener(new MediaPlayer.OnPreparedListener(){public void onPrepared(MediaPlayer p){ready=true;bookmarkReady=true;if(resumePosition>0){p.seekTo(Math.min(resumePosition,Math.max(0,p.getDuration()-1000)));resumePosition=0;}if(audio.requestAudioFocus(PlaybackService.this,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED){p.start();state="Redare";}else state="Pauză · alt sunet";publish();}});
            player.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener(){public void onSeekComplete(MediaPlayer p){saveBookmark();}});
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){public void onCompletion(MediaPlayer p){saveBookmark();bookmarkReady=false;if(queue.advance(true,true))playCurrent();else{MusicResume.schedule(PlaybackService.this,queue,0);state="Terminat";stopSelf();}}});
            player.setOnErrorListener(new MediaPlayer.OnErrorListener(){public boolean onError(MediaPlayer p,int what,int extra){state="Fișierul nu poate fi redat";stopSelf();return true;}});player.prepareAsync();
        }catch(Exception failure){state="Redarea a eșuat";stopSelf();}
    }
    void toggle(){if(!ready||player==null)return;try{if(player.isPlaying()){player.pause();state="Pauză";}else if(audio.requestAudioFocus(this,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED){player.start();state="Redare";}}catch(IllegalStateException ignored){}saveBookmark();publish();}
    void seek(int delta){if(ready&&player!=null)try{player.seekTo(Math.max(0,Math.min(player.getDuration(),player.getCurrentPosition()+delta)));}catch(IllegalStateException ignored){}publish();}
    int position(){try{return ready&&player!=null?player.getCurrentPosition():0;}catch(IllegalStateException ignored){return 0;}}
    int duration(){try{return ready&&player!=null?player.getDuration():0;}catch(IllegalStateException ignored){return 0;}}
    int audioSession(){try{return ready&&player!=null?player.getAudioSessionId():0;}catch(IllegalStateException ignored){return 0;}}
    boolean playing(){try{return ready&&player!=null&&player.isPlaying();}catch(IllegalStateException ignored){return false;}}
    public void onAudioFocusChange(int focus){if(focus<=0&&ready&&player!=null)try{player.pause();state="Pauză · alt sunet";}catch(IllegalStateException ignored){}publish();}
    void next(boolean forward){saveBookmark();bookmarkReady=false;if(queue.advance(forward,false))playCurrent();else bookmarkReady=true;}
    void choose(int at){if(at>=0&&at<queue.sources.size()){saveBookmark();bookmarkReady=false;queue.index=at;playCurrent();}}
    void remove(int at){saveBookmark();bookmarkReady=false;boolean current=queue.remove(at);if(queue.index<0)stopSelf();else if(current)playCurrent();else {bookmarkReady=true;saveBookmark();publish();}}
    void move(int from,int to){queue.move(from,to);saveBookmark();publish();}
    void modes(boolean shuffle,int repeat){queue.shuffle=shuffle;queue.repeat=Math.max(0,Math.min(2,repeat));getSharedPreferences("music-player",0).edit().putBoolean("shuffle",shuffle).putInt("repeat",queue.repeat).apply();publish();}
    Bitmap cover(){return cover;}
    private void loadCover(final Uri uri){cover=null;final int request=generation;metadata.execute(new Runnable(){public void run(){if(request!=generation)return;Bitmap image=null;MediaMetadataRetriever retriever=new MediaMetadataRetriever();try{retriever.setDataSource(PlaybackService.this,uri);byte[] art=retriever.getEmbeddedPicture();if(art!=null&&art.length<=2*1024*1024){BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(art,0,art.length,bounds);bounds.inSampleSize=1;while(bounds.outWidth/bounds.inSampleSize>192||bounds.outHeight/bounds.inSampleSize>192)bounds.inSampleSize*=2;bounds.inJustDecodeBounds=false;image=BitmapFactory.decodeByteArray(art,0,art.length,bounds);}}catch(Exception ignored){}finally{retriever.release();}final Bitmap result=image;main.post(new Runnable(){public void run(){if(instance!=PlaybackService.this||request!=generation){if(result!=null)result.recycle();return;}cover=result;publish();}});}});}
    private void publish(){if(mediaSession==null)return;boolean playing=playing();mediaSession.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_SEEK_TO|PlaybackState.ACTION_STOP).setState(playing?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,position(),playing?1:0).build());
        MediaMetadata.Builder meta=new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,title).putLong(MediaMetadata.METADATA_KEY_DURATION,duration());if(cover!=null)meta.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,cover);mediaSession.setMetadata(meta.build());
        PendingIntent open=PendingIntent.getActivity(this,401,new Intent(this,MediaActivity.class).putExtra("now",true),PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent toggle=PendingIntent.getService(this,403,new Intent(this,PlaybackService.class).setAction("toggle"),PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,402,new Intent(this,PlaybackService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder n=new Notification.Builder(this,"leo-music").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title).setContentText(queue.mode()).setContentIntent(open).setOngoing(true).setVisibility(Notification.VISIBILITY_PUBLIC).setStyle(new Notification.MediaStyle().setMediaSession(mediaSession.getSessionToken()).setShowActionsInCompactView(0)).addAction(new Notification.Action.Builder(null,playing?"Pauză":"Redă",toggle).build()).addAction(new Notification.Action.Builder(null,"Oprește",stop).build());if(cover!=null)n.setLargeIcon(cover);startForeground(401,n.build());sendBroadcast(new Intent("ro.leo.visual.MUSIC_CHANGED").setPackage(getPackageName()));
    }
    private void release(){generation++;ready=false;bookmarkReady=false;cover=null;if(player!=null){player.release();player=null;}if(wma!=null){wma.cancel();wma=null;}}
    public void onDestroy(){saveBookmark();main.removeCallbacks(bookmark);MusicSleep.cancel(this);release();metadata.shutdownNow();mediaSession.setActive(false);mediaSession.release();cover=null;audio.abandonAudioFocus(this);stopForeground(true);if("Redare".equals(state)||state.startsWith("Pauză")||state.startsWith("Se pregătește"))state="Oprit";instance=null;super.onDestroy();}
}
