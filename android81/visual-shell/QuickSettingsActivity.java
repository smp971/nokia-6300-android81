package ro.leo.visual;
import ro.leo.i18n.L;
import android.bluetooth.BluetoothAdapter;
import android.content.*;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.util.*;

/** Compact live controls, preserving the dedicated all-stream volume entry. */
public final class QuickSettingsActivity extends LeoActivity {
    private boolean volumePage,registered;
    private int quickSelected,volumeSelected;
    private final int[] streams={AudioManager.STREAM_MUSIC,AudioManager.STREAM_RING,AudioManager.STREAM_ALARM,AudioManager.STREAM_VOICE_CALL};
    QuickSettingsPanel panel;
    private android.widget.LinearLayout dashboard;
    QuickSettingsControls controls;
    private final Handler changes=new Handler();
    private final Runnable refresh=new Runnable(){public void run(){if(uiActive){if(volumePage)volumes(volumeSelected);else refreshPanel();}}};
    private void queueRefresh(){changes.removeCallbacks(refresh);changes.postDelayed(refresh,40);}
    private final BroadcastReceiver updates=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){if(!"android.media.VOLUME_CHANGED_ACTION".equals(intent.getAction())||volumePage||intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE",AudioManager.STREAM_MUSIC)==AudioManager.STREAM_MUSIC)queueRefresh();}};
    private final ContentObserver settings=new ContentObserver(changes){public void onChange(boolean self){queueRefresh();}};
    protected void onCreate(Bundle state){super.onCreate(state);controls=new QuickSettingsControls(this);volumePage=state!=null?state.getBoolean("volumePage"):getIntent().getBooleanExtra("volumes",false);if(state!=null){quickSelected=state.getInt("quickSelected");volumeSelected=state.getInt("volumeSelected");}}
    protected void onSaveInstanceState(Bundle out){out.putBoolean("volumePage",volumePage);out.putInt("quickSelected",quickSelected);out.putInt("volumeSelected",volumeSelected);super.onSaveInstanceState(out);}
    protected void onResume(){
        super.onResume();
        if(!registered){IntentFilter filter=new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);filter.addAction("android.media.VOLUME_CHANGED_ACTION");filter.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED");filter.addAction("android.location.MODE_CHANGED");registerReceiver(updates,filter);
            getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),false,settings);getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),false,settings);getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.LOCATION_MODE),false,settings);registered=true;}
        if(volumePage)volumes(volumeSelected);else show();
    }
    protected void onPause(){if(registered){unregisterReceiver(updates);getContentResolver().unregisterContentObserver(settings);registered=false;}changes.removeCallbacks(refresh);super.onPause();}
    void onAppearanceChanged(){if(panel!=null)panel.style(palette,Appearance.textScale(this));}
    private void show(){
        volumePage=false;
        if(root==dashboard&&panel!=null){refreshPanel();panel.requestFocus();return;}
        begin(L.t("Configurări rapide"));dashboard=root;
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setFocusable(false);scroll.setClipToPadding(false);
        panel=new QuickSettingsPanel(this,new QuickSettingsPanel.Actions(){public void activate(int item){QuickSettingsActivity.this.activate(item);}public void value(int item,int value){setLevel(item,value);}public void focused(int item){quickSelected=item;updateFooter();}},quickSelected);
        scroll.addView(panel,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        left=new Runnable(){public void run(){menu();}};refreshPanel();updateFooter();panel.requestFocus();
    }
    private void refreshPanel(){if(panel==null||root!=dashboard)return;panel.state(controls.read());updateFooter();}
    private void updateFooter(){if(root!=dashboard)return;footer(L.t("Opțiuni"),quickSelected==4?L.t("Alege"):quickSelected>=5?L.t("Nivel"):L.t("Comută"),L.t("Înapoi"));}
    private void setLevel(int item,int value){try{if(item==QuickSettingsPanel.BRIGHTNESS)controls.brightness(value);else if(item==QuickSettingsPanel.MEDIA)controls.media(value);}catch(RuntimeException error){notice(error.getMessage()==null?L.t("Nivelul nu poate fi schimbat"):error.getMessage());}refreshPanel();}
    private void activate(int item){
        try{if(item==0)controls.toggleWifi();else if(item==1)controls.toggleBluetooth();else if(item==2)controls.toggleLocation();else if(item==3){hotspot();return;}else if(item==4){SoundProfiles.select(this,new Runnable(){public void run(){refreshPanel();}});return;}else{levels(item);return;}refreshPanel();}
        catch(RuntimeException error){notice(error.getMessage()==null?L.t("Setarea nu poate fi schimbată"):error.getMessage());refreshPanel();}
    }
    private void levels(final int item){
        QuickSettingsControls.State state=controls.read();int max=item==5?20:state.mediaMax;String[] choices=new String[item==5?20:max+1];for(int i=0;i<choices.length;i++)choices[i]=item==5?((i+1)*5)+"%":L.t("Nivel ")+i+" / "+max;
        new KeypadDialog(this,QuickSettingsControls.TITLES[item],choices,new KeypadDialog.Action(){public void select(int index){setLevel(item,item==5?(index+1)*5:index);}}).initialSelection(item==5?Math.round(state.brightnessPercent()/5f)-1:state.media).show();
    }
    private void hotspot(){
        int state=HotspotControl.state(this);if(state==10||state==12){notice(L.t("Așteaptă schimbarea stării"));return;}
        try{
            if(state==13){HotspotControl.stop(this);refreshPanel();return;}
            if(state!=11||!HotspotControl.configured(this)){notice(L.t("Configurează mai întâi WPA2 și parola"));detail("hotspot");return;}
            if(((WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE)).isWifiEnabled())options(L.t("Hotspot oprește Wi-Fi"),new String[]{L.t("Anulează"),L.t("Pornește hotspot")},new Choice(){public void pick(int choice){if(choice==1)startHotspot();}});else startHotspot();
        }catch(Exception error){notice(L.t("Serviciul hotspot nu este disponibil"));}
    }
    private void startHotspot(){try{HotspotControl.start(this,new ResultReceiver(changes){protected void onReceiveResult(int code,Bundle data){if(!isFinishing()&&!isDestroyed()){if(code!=0)notice(L.t("Eroare hotspot · cod ")+code);queueRefresh();}}});refreshPanel();}catch(Exception error){notice(L.t("Serviciul hotspot nu este disponibil"));}}
    private void detail(String kind){startActivity(new Intent(this,ConnectivityActivity.class).putExtra("kind",kind));}
    private void menu(){options(L.t("Configurări rapide"),new String[]{"Wi-Fi","Bluetooth",L.t("Localizare"),L.t("Hotspot Wi-Fi"),L.t("SIM-uri și date mobile"),L.t("Volume separate"),ContextHelp.label()},new Choice(){public void pick(int item){if(item==0)detail("wifi");else if(item==1)detail("bluetooth");else if(item==2)startActivity(new Intent(QuickSettingsActivity.this,PrivacySettingsActivity.class));else if(item==3)detail("hotspot");else if(item==4)startActivity(new Intent(QuickSettingsActivity.this,MobileSettingsActivity.class));else if(item==5)volumes(0);else ContextHelp.open(QuickSettingsActivity.this,"QuickSettingsActivity");}});}
    static String wifiState(int state){switch(state){case WifiManager.WIFI_STATE_ENABLED:return L.t("Pornit");case WifiManager.WIFI_STATE_DISABLED:return L.t("Oprit");case WifiManager.WIFI_STATE_ENABLING:return L.t("Se pornește…");case WifiManager.WIFI_STATE_DISABLING:return L.t("Se oprește…");default:return L.t("Indisponibil");}}
    static String bluetoothState(int state){switch(state){case BluetoothAdapter.STATE_ON:return L.t("Pornit");case BluetoothAdapter.STATE_OFF:return L.t("Oprit");case BluetoothAdapter.STATE_TURNING_ON:return L.t("Se pornește…");case BluetoothAdapter.STATE_TURNING_OFF:return L.t("Se oprește…");default:return L.t("Indisponibil");}}
    String rowMemoryKey(String title,int at,String label){return title+":"+at;}
    boolean stableRowIcons(){return true;}
    android.graphics.drawable.Drawable rowIcon(String title,int index){return new QuickIcon(6,dp(29));}
    void onListSelection(String title,int index){if(title.startsWith(L.t("Volum")))volumeSelected=index;}
    private void volumes(int selected){volumePage=true;volumeSelected=selected;final AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);String[] names={L.t("Muzică"),L.t("Sonerie"),L.t("Alarmă"),L.t("Apel")};ArrayList<String> labels=new ArrayList<String>();for(int i=0;i<streams.length;i++)labels.add(names[i]+" · "+audio.getStreamVolume(streams[i])+" / "+audio.getStreamMaxVolume(streams[i]));if(replaceRows(L.t("Volum · ← / → nivel"),labels))return;list(L.t("Volum · ← / → nivel"),labels,selected,new Choice(){public void pick(final int slot){int max=audio.getStreamMaxVolume(streams[slot]);String[] levels=new String[max+1];for(int n=0;n<=max;n++)levels[n]=L.t("Nivel ")+n+" / "+max;options(L.t("Volum ")+new String[]{L.t("muzică"),L.t("sonerie"),L.t("alarmă"),L.t("apel")}[slot],levels,new Choice(){public void pick(int value){try{audio.setStreamVolume(streams[slot],value,0);}catch(RuntimeException e){notice(L.t("Nivelul nu poate fi schimbat"));}volumes(slot);}});}},L.t("Înapoi"),new Runnable(){public void run(){show();}});back=new Runnable(){public void run(){show();}};}
    public boolean onKeyDown(int key,android.view.KeyEvent e){if(volumePage&&(key==android.view.KeyEvent.KEYCODE_DPAD_LEFT||key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT)){try{((AudioManager)getSystemService(AUDIO_SERVICE)).adjustStreamVolume(streams[volumeSelected],key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT?1:-1,0);}catch(RuntimeException error){notice(L.t("Volumul nu poate fi schimbat"));}volumes(volumeSelected);return true;}return super.onKeyDown(key,e);}
}
