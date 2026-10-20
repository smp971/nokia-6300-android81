package ro.leo.visual;
import android.app.*;import android.content.*;import android.media.*;import android.os.*;import java.io.*;
/** Explicitly invoked, announced eight-second microphone test. Own recording stays local. */
public final class RecorderTests extends Instrumentation {
    public void onCreate(Bundle b){start();}
    private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    public void onStart(){final Context c=getTargetContext();Bundle out=new Bundle();SharedPreferences prefs=c.getSharedPreferences("storage",0);String previous=prefs.getString("recordings",null);boolean started=false;try{
        check(RecordingService.instance==null&&PlaybackService.instance==null,"recorder and music must be idle");check(RecordingLocations.folders(c).containsKey("sd1"),"SD available");prefs.edit().putString("recordings","sd1").commit();
        runOnMainSync(new Runnable(){public void run(){c.startService(new Intent(c,RecordingService.class).setAction("record"));}});started=true;
        final File[] created=new File[1];
        for(int i=0;i<50&&created[0]==null;i++){runOnMainSync(new Runnable(){public void run(){try{if(RecordingService.instance!=null&&"Înregistrare".equals(RecordingService.state)){java.lang.reflect.Field complete=RecordingService.class.getDeclaredField("complete");complete.setAccessible(true);created[0]=(File)complete.get(RecordingService.instance);}}catch(Exception e){throw new RuntimeException(e);}}});if(created[0]==null)SystemClock.sleep(100);}
        check(created[0]!=null,"recorder initialized: "+RecordingService.state);File file=created[0];SystemClock.sleep(8000);
        runOnMainSync(new Runnable(){public void run(){c.startService(new Intent(c,RecordingService.class).setAction("stop"));}});for(int i=0;i<30&&RecordingService.instance!=null;i++)SystemClock.sleep(100);check(RecordingService.instance==null&&file!=null&&file.length()>1000,"finalized recording");
        MediaMetadataRetriever metadata=new MediaMetadataRetriever();long duration;try{metadata.setDataSource(file.getPath());duration=Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));}finally{metadata.release();}check(duration>=7000&&duration<=11000,"bounded audio duration");
        final android.net.Uri uri=LeoFileProvider.uri(c,file);runOnMainSync(new Runnable(){public void run(){c.startService(new Intent(c,PlaybackService.class).setData(uri).putExtra("title","Probă reportofon SD"));}});
        for(int i=0;i<50&&(PlaybackService.instance==null||!PlaybackService.instance.playing());i++)SystemClock.sleep(100);check(PlaybackService.instance!=null&&PlaybackService.instance.playing(),"recorded SD audio plays through Leo service");SystemClock.sleep(duration+200);
        out.putString("result","PASS SD microphone recording, finalized M4A, duration "+duration+" ms, Leo playback started; human audibility pending");
    }catch(Throwable e){out.putString("error",android.util.Log.getStackTraceString(e));}finally{if(started)runOnMainSync(new Runnable(){public void run(){c.stopService(new Intent(c,RecordingService.class));c.stopService(new Intent(c,PlaybackService.class));}});if(previous==null)prefs.edit().remove("recordings").commit();else prefs.edit().putString("recordings",previous).commit();}finish(out.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,out);}
}
