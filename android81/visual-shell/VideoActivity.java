package ro.leo.visual;
import ro.leo.i18n.L;
import android.media.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

public final class VideoActivity extends LeoActivity implements SurfaceHolder.Callback,AudioManager.OnAudioFocusChangeListener {
    private MediaPlayer player;private SurfaceView surface;private boolean ready;private int savedPosition;private AudioManager audio;
    protected void onCreate(Bundle state){super.onCreate(state);audio=(AudioManager)getSystemService(AUDIO_SERVICE);if(state!=null)savedPosition=state.getInt("position");
        begin("Video Leo");surface=new SurfaceView(this);surface.getHolder().addCallback(this);root.addView(surface,new LinearLayout.LayoutParams(-1,0,1));
        left=new Runnable(){public void run(){notice(L.t("OK: pauză/redare · ←/→: ±10 s · ↑/↓: volum"));}};footer(L.t("Comenzi"),L.t("Pauză / Redă"),L.t("Înapoi"));surface.setFocusable(true);surface.requestFocus();
    }
    private void prepare(){
        if(player!=null||surface==null||!surface.getHolder().getSurface().isValid())return;
        if(!PlaybackService.hardwareReady()){notice(L.t("Driverul audio este inactiv; redarea nu a fost pornită"));return;}
        if(audio.requestAudioFocus(this,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){notice(L.t("Sunet ocupat"));return;}
        try{player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build());player.setDataSource(this,getIntent().getData());player.setDisplay(surface.getHolder());
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener(){public void onPrepared(MediaPlayer p){ready=true;p.seekTo(savedPosition);p.start();surface.setKeepScreenOn(true);}});
            player.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener(){public void onVideoSizeChanged(MediaPlayer p,int w,int h){if(w>0&&h>0){int available=root.getHeight()-dp(76);int width=root.getWidth();LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(width,Math.max(1,Math.min(available,(int)(width*(double)h/w))));params.gravity=Gravity.CENTER;surface.setLayoutParams(params);}}});
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){public void onCompletion(MediaPlayer p){surface.setKeepScreenOn(false);}});
            player.setOnErrorListener(new MediaPlayer.OnErrorListener(){public boolean onError(MediaPlayer p,int what,int extra){notice(L.t("Format sau decodor video indisponibil"));release();return true;}});player.prepareAsync();
        }catch(Exception failure){notice(L.t("Fișierul video nu poate fi deschis"));release();}
    }
    private void release(){ready=false;if(player!=null){try{savedPosition=player.getCurrentPosition();}catch(IllegalStateException ignored){}player.release();player=null;}if(surface!=null)surface.setKeepScreenOn(false);audio.abandonAudioFocus(this);}
    public void surfaceCreated(SurfaceHolder h){prepare();}public void surfaceChanged(SurfaceHolder h,int f,int w,int height){}public void surfaceDestroyed(SurfaceHolder h){release();}
    protected void onResume(){super.onResume();if(surface!=null)surface.post(new Runnable(){public void run(){prepare();}});}
    protected void onPause(){release();super.onPause();}
    protected void onSaveInstanceState(Bundle state){if(ready&&player!=null)try{savedPosition=player.getCurrentPosition();}catch(IllegalStateException ignored){}state.putInt("position",savedPosition);super.onSaveInstanceState(state);}
    public void onAudioFocusChange(int focus){if(focus<=0&&ready&&player!=null)try{player.pause();}catch(IllegalStateException ignored){}}
    public boolean onKeyDown(int key,KeyEvent event){
        try{
            if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){if(event.getRepeatCount()==0&&ready){if(player.isPlaying()){player.pause();surface.setKeepScreenOn(false);}else if(audio.requestAudioFocus(this,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED){player.start();surface.setKeepScreenOn(true);}}return true;}
            if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){if(ready)player.seekTo(Math.max(0,Math.min(player.getDuration(),player.getCurrentPosition()+(key==KeyEvent.KEYCODE_DPAD_LEFT?-10000:10000))));return true;}
            if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,key==KeyEvent.KEYCODE_DPAD_UP?1:-1,AudioManager.FLAG_SHOW_UI);return true;}
        }catch(IllegalStateException ignored){}return super.onKeyDown(key,event);
    }
}
