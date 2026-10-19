package ro.leo.audiotest;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.media.AudioTrack;
import android.media.AudioManager;
import android.media.AudioFormat;
import android.util.Log;
import android.widget.TextView;
public class AudioTest extends Activity {
    AudioTrack track;
    android.media.MediaPlayer player;
    AudioManager audio;
    int previousMode;
    boolean previousSpeaker,restoreRouting;
    private void restoreRoute(){if(restoreRouting){audio.setSpeakerphoneOn(previousSpeaker);audio.setMode(previousMode);restoreRouting=false;}}
    public void onCreate(Bundle b){
        super.onCreate(b);final TextView label=new TextView(this);label.setText("Android AudioTrack test");setContentView(label);
        final String source=getIntent().getStringExtra("source");
        if(source!=null){
            label.setText("MediaPlayer: "+source);
            try {
                player=new android.media.MediaPlayer();
                player.setAudioStreamType("music".equals(getIntent().getStringExtra("stream"))?AudioManager.STREAM_MUSIC:AudioManager.STREAM_RING);
                player.setVolume(0.25f,0.25f);
                player.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener(){
                    public boolean onError(android.media.MediaPlayer p,int what,int extra){
                        Log.e("LeoAudioTest","MEDIA_ERROR source="+source+" what="+what+" extra="+extra);return true;
                    }});
                player.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener(){
                    public void onPrepared(android.media.MediaPlayer p){
                        Log.i("LeoAudioTest","MEDIA_PREPARED duration="+p.getDuration());p.start();
                        new Handler().postDelayed(new Runnable(){public void run(){
                            if(player!=null){Log.i("LeoAudioTest","MEDIA_POSITION="+player.getCurrentPosition());player.release();player=null;}
                        }},3000);
                    }});
                if(source.startsWith("content:"))player.setDataSource(this,android.net.Uri.parse(source));
                else player.setDataSource(source);
                player.prepareAsync();
            } catch(Exception e){Log.e("LeoAudioTest","MEDIA_FAILURE",e);label.setText(e.toString());}
            return;
        }
        try {
            final int seconds=Math.max(2,Math.min(10,getIntent().getIntExtra("seconds",2)));
            boolean receiver="earpiece".equals(getIntent().getStringExtra("route"));
            if(receiver){
                audio=(AudioManager)getSystemService(AUDIO_SERVICE);previousMode=audio.getMode();previousSpeaker=audio.isSpeakerphoneOn();
                audio.setMode(AudioManager.MODE_IN_COMMUNICATION);restoreRouting=true;audio.setSpeakerphoneOn(false);
                label.setText("Test receptor apel · fără apel real");
            }
            short[] pcm=new short[48000*2*seconds];
            for(int i=0;i<48000*seconds;i++){
                double t=i/48000.0,phase=t%1.0;
                double fade=phase<0.5?Math.max(0,Math.min(1,Math.min(phase/0.015,(0.5-phase)/0.015))):0;
                short v=(short)(32767*0.1*fade*Math.sin(2*Math.PI*700*t));pcm[2*i]=v;pcm[2*i+1]=v;
            }
            track=new AudioTrack(receiver?AudioManager.STREAM_VOICE_CALL:AudioManager.STREAM_MUSIC,48000,AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,pcm.length*2,AudioTrack.MODE_STATIC);
            int wrote=track.write(pcm,0,pcm.length);track.setVolume(0.4f);track.play();
            Log.i("LeoAudioTest","started state="+track.getState()+" shorts="+wrote);
            new Handler().postDelayed(new Runnable(){public void run(){if(track!=null){android.media.AudioDeviceInfo device=track.getRoutedDevice();Log.i("LeoAudioTest","ROUTED_DEVICE="+(device==null?-1:device.getType()));}}},500);
            new Handler().postDelayed(new Runnable(){public void run(){
                if(track!=null){int frames=track.getPlaybackHeadPosition();
                    Log.i("LeoAudioTest","LEO_ANDROID_AUDIO_FRAMES="+frames);label.setText("AudioTrack frames: "+frames);
                    track.stop();track.release();track=null;}restoreRoute();
            }},seconds*1000+1000);
        } catch(Exception e){restoreRoute();Log.e("LeoAudioTest","Playback failed",e);label.setText(e.toString());}
    }
    public void onDestroy(){if(player!=null){player.release();player=null;}if(track!=null){track.release();track=null;}restoreRoute();super.onDestroy();}
}
