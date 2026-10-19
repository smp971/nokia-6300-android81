package ro.leo.visual;
import android.content.*;
import android.os.*;
import android.view.KeyEvent;
import android.widget.*;
import java.io.*;
import java.util.*;

public final class RecorderActivity extends LeoActivity {
    private final Handler handler=new Handler();private TextView status;private boolean main;
    private final Runnable tick=new Runnable(){public void run(){if(main&&status!=null){status.setText(RecordingService.state+(RecordingService.instance==null?"":"\n"+MediaActivity.format((int)RecordingService.elapsed())));handler.postDelayed(this,1000);}}};
    protected void onCreate(Bundle state){super.onCreate(state);show();}
    protected void onResume(){super.onResume();if(main){handler.removeCallbacks(tick);handler.post(tick);}}
    protected void onPause(){handler.removeCallbacks(tick);super.onPause();}
    private void show(){main=true;begin("Reportofon Leo");status=text("",21);root.addView(status,new LinearLayout.LayoutParams(-1,0,1));root.addView(text("OK: înregistrează / pauză\nSoft stânga: opțiuni\nFișiere → Documente Leo → Recordings",15));
        left=new Runnable(){public void run(){options("Reportofon",new String[]{"Oprește și salvează","Înregistrări"},new Choice(){public void pick(int i){if(i==0){if(RecordingService.instance!=null)startService(new Intent(RecorderActivity.this,RecordingService.class).setAction("stop"));}else recordings();}});}};footer("Opțiuni","Rec / Pauză","Înapoi");handler.removeCallbacks(tick);handler.post(tick);
    }
    private void recordings(){main=false;handler.removeCallbacks(tick);final ArrayList<File> files=new ArrayList<File>();File[] found=new File(getFilesDir(),"documents/Recordings").listFiles();if(found!=null)for(File f:found)if(f.isFile()&&f.getName().endsWith(".m4a"))files.add(f);Collections.sort(files,new Comparator<File>(){public int compare(File a,File b){return b.getName().compareTo(a.getName());}});ArrayList<String> names=new ArrayList<String>();for(File f:files)names.add(f.getName());
        list("Înregistrări Leo",names,0,new Choice(){public void pick(int i){final File f=files.get(i);options(f.getName(),new String[]{"Redă","Șterge"},new Choice(){public void pick(int action){if(action==0){if(RecordingService.instance!=null){notice("Oprește înregistrarea întâi");return;}if(!PlaybackService.hardwareReady()){notice("Driverul audio este inactiv");return;}try{startService(new Intent(RecorderActivity.this,PlaybackService.class).setData(LeoFileProvider.uri(RecorderActivity.this,f)).putExtra("title",f.getName()));}catch(IOException failure){notice("Fișier indisponibil");}}else options("Ștergi înregistrarea?",new String[]{"Păstrează","Șterge"},new Choice(){public void pick(int yes){if(yes==1){if(!f.delete())notice("Ștergerea a eșuat");recordings();}}});}});}},"",null);back=new Runnable(){public void run(){show();}};
    }
    public boolean onKeyDown(int key,KeyEvent event){if(main&&(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)){if(event.getRepeatCount()==0){if(!PlaybackService.hardwareReady())notice("Driverul audio este inactiv; microfonul nu a fost pornit");else if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},70);else startService(new Intent(this,RecordingService.class).setAction(RecordingService.instance==null?"record":"pause"));}return true;}return super.onKeyDown(key,event);}
}
