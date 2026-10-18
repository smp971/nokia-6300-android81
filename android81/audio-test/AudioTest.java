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
    public void onCreate(Bundle b){
        super.onCreate(b);final TextView label=new TextView(this);label.setText("Android AudioTrack test");setContentView(label);
        try {
            short[] pcm=new short[48000*2*2];
            for(int i=0;i<96000;i++){
                double t=i/48000.0,phase=t%1.0;
                double fade=phase<0.5?Math.max(0,Math.min(1,Math.min(phase/0.015,(0.5-phase)/0.015))):0;
                short v=(short)(32767*0.1*fade*Math.sin(2*Math.PI*700*t));pcm[2*i]=v;pcm[2*i+1]=v;
            }
            track=new AudioTrack(AudioManager.STREAM_MUSIC,48000,AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,pcm.length*2,AudioTrack.MODE_STATIC);
            int wrote=track.write(pcm,0,pcm.length);track.setVolume(0.4f);track.play();
            Log.i("LeoAudioTest","started state="+track.getState()+" shorts="+wrote);
            new Handler().postDelayed(new Runnable(){public void run(){
                if(track!=null){int frames=track.getPlaybackHeadPosition();
                    Log.i("LeoAudioTest","LEO_ANDROID_AUDIO_FRAMES="+frames);label.setText("AudioTrack frames: "+frames);
                    track.stop();track.release();track=null;}
            }},3000);
        } catch(Exception e){Log.e("LeoAudioTest","Playback failed",e);label.setText(e.toString());}
    }
    public void onDestroy(){if(track!=null){track.release();track=null;}super.onDestroy();}
}
