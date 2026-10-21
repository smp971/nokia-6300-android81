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
    protected void onCreate(Bundle state){super.onCreate(state);video="video".equals(getIntent().getStringExtra("kind"));if(!video&&getIntent().getData()!=null){startService(new Intent(this,PlaybackService.class).setData(getIntent().getData()).putExtra("title",getIntent().getData().getLastPathSegment()));now();}else if(getIntent().getBooleanExtra("now",false))now();else load();}
    protected void onResume(){super.onResume();resumed=true;if(face!=null)face.appearance();if(playerScreen){handler.removeCallbacks(refresh);handler.post(refresh);}}
    protected void onPause(){resumed=false;handler.removeCallbacks(refresh);if(face!=null)face.release();super.onPause();}
    private void load(){
        if(!video){startActivity(new Intent(this,MusicLibraryActivity.class));finish();return;}
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
        ArrayList<String> sources=new ArrayList<String>(),names=new ArrayList<String>();for(int i=0;i<uris.size();i++){sources.add(uris.get(i).toString());names.add(titles.get(i).split("\n")[0]);}startService(new Intent(this,PlaybackService.class).putStringArrayListExtra("queue",sources).putStringArrayListExtra("titles",names).putExtra("index",selected));now();
    }
    private void now(){
        playerScreen=true;if(face!=null)face.release();face=new MusicFace(this);setContentView(face);
        left=new Runnable(){public void run(){final PlaybackService service=PlaybackService.instance;options("Redare",new String[]{"Bibliotecă","Coada de redare","Piesa anterioară","Piesa următoare",service!=null&&service.queue.shuffle?"Aleatoriu · pornit":"Aleatoriu · oprit","Repetare","Oprește","Salvează lista","Versuri locale · LRC","Oprire temporizată","Reia ultima sesiune"},new Choice(){public void pick(int i){PlaybackService s=PlaybackService.instance;if(i==8)startActivity(new Intent(MediaActivity.this,LyricsActivity.class));else if(i==9)sleepMenu();else if(i==10)resumeLast();else if(i==7)MusicLists.saveQueue(MediaActivity.this);else if(i==0)load();else if(i==6){stopService(new Intent(MediaActivity.this,PlaybackService.class));load();}else if(s==null)notice("Nu există redare activă");else if(i==1)queue();else if(i==2||i==3)s.next(i==3);else if(i==4)s.modes(!s.queue.shuffle,s.queue.repeat);else options("Repetare",new String[]{"Fără repetare","Repetă lista","Repetă piesa"},new Choice(){public void pick(int n){PlaybackService s=PlaybackService.instance;if(s!=null)s.modes(s.queue.shuffle,n);}});}});}};
        back=new Runnable(){public void run(){handler.removeCallbacks(refresh);load();}};handler.removeCallbacks(refresh);handler.post(refresh);
    }
    private void resumeLast(){startService(new Intent(this,PlaybackService.class).setAction("resume"));now();}
    private void sleepMenu(){final PlaybackService s=PlaybackService.instance;if(s==null){notice("Pornește o piesă înainte de temporizator");return;}long left=MusicSleep.remaining(this);options("Oprire · "+(left>0?(left+59999)/60000+" min rămase":"inactivă"),new String[]{"5 minute","15 minute","30 minute","60 minute","Anulează oprirea"},new Choice(){public void pick(int n){if(n==4)MusicSleep.cancel(MediaActivity.this);else MusicSleep.set(MediaActivity.this,new int[]{5,15,30,60}[n]);notice(n==4?"Oprire anulată":"Muzica se va opri automat");}});}
    private void queue(){playerScreen=false;handler.removeCallbacks(refresh);if(face!=null)face.release();final PlaybackService s=PlaybackService.instance;if(s==null){load();return;}ArrayList<String> labels=new ArrayList<String>();for(int i=0;i<s.queue.titles.size();i++)labels.add((i==s.queue.index?"▶ ":"")+s.queue.titles.get(i));list("Coada de redare",labels,Math.max(0,s.queue.index),new Choice(){public void pick(final int at){options("Piesa "+(at+1),new String[]{"Redă","Mută mai sus","Mută mai jos","Elimină din coadă"},new Choice(){public void pick(int action){if(PlaybackService.instance!=s){load();return;}if(action==0){s.choose(at);now();}else{if(action==3)s.remove(at);else s.move(at,action==1?Math.max(0,at-1):Math.min(s.queue.titles.size()-1,at+1));queue();}}});}},"Player",new Runnable(){public void run(){now();}});back=new Runnable(){public void run(){now();}};}
    public boolean onKeyDown(int key,KeyEvent event){
        if(playerScreen){PlaybackService s=PlaybackService.instance;
            if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_5){if(event.getRepeatCount()==0&&s!=null){s.toggle();if(face!=null)face.tick();}return true;}
            if(key==KeyEvent.KEYCODE_4||key==KeyEvent.KEYCODE_6){if(event.getRepeatCount()==0&&s!=null){s.next(key==KeyEvent.KEYCODE_6);if(face!=null)face.tick();}return true;}
            if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){if(s!=null)s.seek(key==KeyEvent.KEYCODE_DPAD_LEFT?-10000:10000);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){((android.media.AudioManager)getSystemService(AUDIO_SERVICE)).adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,key==KeyEvent.KEYCODE_DPAD_UP?1:-1,android.media.AudioManager.FLAG_SHOW_UI);return true;}
        }return super.onKeyDown(key,event);
    }
}
