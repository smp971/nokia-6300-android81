package ro.leo.visual;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.widget.*;
import java.util.*;

public final class MediaActivity extends LeoActivity {
    private boolean video,playerScreen;private int selected;private final ArrayList<Uri> uris=new ArrayList<Uri>();private final ArrayList<String> titles=new ArrayList<String>();
    private final Handler handler=new Handler();private MusicFace face;private boolean resumed;
    private final Runnable refresh=new Runnable(){public void run(){if(resumed&&playerScreen&&face!=null){face.tick();PlaybackService s=PlaybackService.instance;handler.postDelayed(this,s!=null&&s.playing()?125:1000);}}};
    static String format(int ms){int s=ms/1000;return String.format(Locale.ROOT,"%d:%02d",s/60,s%60);}
    protected void onCreate(Bundle state){super.onCreate(state);video="video".equals(getIntent().getStringExtra("kind"));if(!video&&getIntent().getData()!=null){startService(new Intent(this,PlaybackService.class).setData(getIntent().getData()).putExtra("title",getIntent().getData().getLastPathSegment()));now();}else load();}
    protected void onResume(){super.onResume();resumed=true;if(face!=null)face.appearance();if(playerScreen){handler.removeCallbacks(refresh);handler.post(refresh);}}
    protected void onPause(){resumed=false;handler.removeCallbacks(refresh);if(face!=null)face.release();super.onPause();}
    private void load(){
        playerScreen=false;handler.removeCallbacks(refresh);if(face!=null)face.release();worker.execute(new Runnable(){public void run(){
            final ArrayList<Uri> found=new ArrayList<Uri>();final ArrayList<String> labels=new ArrayList<String>();final Uri base=video?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            try(Cursor c=getContentResolver().query(base,new String[]{"_id","_display_name","duration"},null,null,"_display_name COLLATE NOCASE ASC")){
                while(c!=null&&c.moveToNext()&&found.size()<500){found.add(ContentUris.withAppendedId(base,c.getLong(0)));labels.add(c.getString(1)+"\n"+format(c.getInt(2)));}
                // AOSP has no WMA extractor, so these may be indexed only as
                // generic files. Leo prepares them with its local decoder.
                if(!video)try(Cursor files=getContentResolver().query(MediaStore.Files.getContentUri("external"),new String[]{"_id","_display_name"},"_display_name LIKE ?",new String[]{"%.wma"},"_display_name COLLATE NOCASE ASC")){
                    while(files!=null&&files.moveToNext()&&found.size()<500){
                        long id=files.getLong(0);Uri audioUri=ContentUris.withAppendedId(base,id);
                        if(!found.contains(audioUri)){found.add(ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"),id));labels.add(files.getString(1)+"\nWMA · pregătire locală");}
                    }
                }
                ui(new Runnable(){public void run(){uris.clear();uris.addAll(found);titles.clear();titles.addAll(labels);library();}});
            }catch(RuntimeException denied){ui(new Runnable(){public void run(){notice("Biblioteca nu poate fi citită");library();}});}
        }});
    }
    private void library(){
        playerScreen=false;handler.removeCallbacks(refresh);if(face!=null)face.release();list(video?"Video Leo":"Muzică Leo",titles,selected,new Choice(){public void pick(int i){selected=i;play();}},"Opțiuni",new Runnable(){public void run(){options("Bibliotecă",new String[]{"Reîncarcă", "Redare curentă", "Oprește muzica"},new Choice(){public void pick(int i){if(i==0)load();else if(i==1)now();else stopService(new Intent(MediaActivity.this,PlaybackService.class));}});}});
    }
    private void play(){
        if(video){startActivity(new Intent(this,VideoActivity.class).setData(uris.get(selected)).putExtra("title",titles.get(selected).split("\n")[0]));return;}
        if(!PlaybackService.hardwareReady()){notice("Driverul audio este inactiv; biblioteca rămâne disponibilă");return;}
        startService(new Intent(this,PlaybackService.class).setData(uris.get(selected)).putExtra("title",titles.get(selected).split("\n")[0]));now();
    }
    private void now(){
        playerScreen=true;face=new MusicFace(this);setContentView(face);
        left=new Runnable(){public void run(){options("Redare",new String[]{"Bibliotecă","Oprește","Piesa anterioară","Piesa următoare"},new Choice(){public void pick(int i){if(i==0)library();else if(i==1){stopService(new Intent(MediaActivity.this,PlaybackService.class));library();}else if(!uris.isEmpty()){selected=Math.max(0,Math.min(uris.size()-1,selected+(i==2?-1:1)));play();}}});}};
        back=new Runnable(){public void run(){handler.removeCallbacks(refresh);library();}};handler.removeCallbacks(refresh);handler.post(refresh);
    }
    public boolean onKeyDown(int key,KeyEvent event){
        if(playerScreen){PlaybackService s=PlaybackService.instance;
            if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){if(event.getRepeatCount()==0&&s!=null){s.toggle();if(face!=null)face.tick();}return true;}
            if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){if(s!=null)s.seek(key==KeyEvent.KEYCODE_DPAD_LEFT?-10000:10000);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){((android.media.AudioManager)getSystemService(AUDIO_SERVICE)).adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,key==KeyEvent.KEYCODE_DPAD_UP?1:-1,android.media.AudioManager.FLAG_SHOW_UI);return true;}
        }return super.onKeyDown(key,event);
    }
}
