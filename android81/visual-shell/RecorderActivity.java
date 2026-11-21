package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.*;
import android.os.*;
import android.view.KeyEvent;
import android.widget.*;

public final class RecorderActivity extends LeoActivity {
    private TextView status;private ProgressBar meter;private boolean main;
    private final ScreenTicker ticker=new ScreenTicker(this,new ScreenTicker.Work(){public long refresh(){return refreshRecording();}});
    private final BroadcastReceiver changed=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){ticker.refresh();}};
    private long refreshRecording(){
        if(!main||status==null)return 0;
        RecordingService service=RecordingService.instance;
        String label=RecordingService.state+(service==null?"":"\n"+MediaActivity.format((int)RecordingService.elapsed()));
        if(!label.contentEquals(status.getText()))status.setText(label);
        int amplitude=service==null?0:service.amplitude();
        int level=amplitude<=0?0:Math.max(0,Math.min(100,(int)(100+20*Math.log10(amplitude/32767.0)*100/60)));
        if(meter.getProgress()!=level)meter.setProgress(level);
        footer(L.t("Opțiuni"),service==null?L.t("Rec"):RecordingService.paused?L.t("Continuă"):L.t("Pauză"),L.t("Înapoi"));
        return service!=null&&!RecordingService.paused?200:0;
    }
    protected void onCreate(Bundle state){super.onCreate(state);show();}
    protected void onResume(){super.onResume();registerReceiver(changed,new IntentFilter(RecordingService.CHANGED));ticker.start();}
    protected void onPause(){ticker.stop();unregisterReceiver(changed);super.onPause();}
    private void show(){main=true;begin(L.t("Reportofon"));status=text("",21);root.addView(status,new LinearLayout.LayoutParams(-1,0,1));meter=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);meter.setMax(100);root.addView(meter,new LinearLayout.LayoutParams(-1,dp(20)));root.addView(text(L.t("Salvare: ")+RecordingLocations.label(this),15));
        left=new Runnable(){public void run(){options(L.t("Reportofon"),new String[]{L.t("Oprește și salvează"),L.t("Înregistrări"),L.t("Locație de salvare"),L.t("Calitatea înregistrării"),ContextHelp.label()},new Choice(){public void pick(int i){if(i==0){if(RecordingService.instance!=null)startService(new Intent(RecorderActivity.this,RecordingService.class).setAction("stop"));}else if(i==1)startActivity(new Intent(RecorderActivity.this,RecorderLibraryActivity.class));else if(i==3)options(L.t("Pentru următoarea înregistrare"),RecorderProfile.NAMES,new Choice(){public void pick(int profile){getSharedPreferences("recorder",0).edit().putInt("quality",profile).apply();notice(L.t("Profil salvat pentru următoarea înregistrare"));}});else if(i==4)ContextHelp.open(RecorderActivity.this,"RecorderActivity");else startActivity(new Intent(RecorderActivity.this,StorageSettingsActivity.class));}});}};footer(L.t("Opțiuni"),L.t("Rec"),L.t("Înapoi"));refreshRecording();ticker.refresh();
    }
    public boolean onKeyDown(int key,KeyEvent event){if(main&&(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)){if(event.getRepeatCount()==0){if(!PlaybackService.hardwareReady())notice(L.t("Driverul audio este inactiv; microfonul nu a fost pornit"));else if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},70);else startService(new Intent(this,RecordingService.class).setAction(RecordingService.instance==null?"record":"pause"));}return true;}return super.onKeyDown(key,event);}
}
