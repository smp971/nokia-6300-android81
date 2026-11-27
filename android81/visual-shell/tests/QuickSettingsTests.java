package ro.leo.visual;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.AudioManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Real native controls; never toggles Wi-Fi, BT, location, hotspot or sound profiles. */
public final class QuickSettingsTests extends Instrumentation {
    private QuickSettingsActivity page;
    private int checks,brightness,mode,media;
    private boolean settingsSaved;
    private AudioManager audio;
    private File shots;
    private PowerManager.WakeLock awake;
    private final Map<String,Map<String,?>> prefs=new LinkedHashMap<>();
    private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    private void ui(final Runnable action){final Throwable[] error={null};runOnMainSync(new Runnable(){public void run(){try{action.run();}catch(Throwable e){error[0]=e;}}});if(error[0]!=null)throw new RuntimeException(error[0]);waitForIdleSync();}
    private Object field(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception error){throw new RuntimeException(error);}}
    private void key(int code){ui(new Runnable(){public void run(){long now=SystemClock.uptimeMillis();KeyEvent down=new KeyEvent(now,now,0,code,0),up=new KeyEvent(now,now+30,1,code,0);KeypadDialog d=KeypadDialog.current();if(d!=null){d.dispatchKeyEvent(down);d.dispatchKeyEvent(up);}else{page.dispatchKeyEvent(down);page.dispatchKeyEvent(up);}}});}
    private void select(int item){ui(new Runnable(){public void run(){page.panel.select(item);}});}
    private void open(boolean volumes){page=(QuickSettingsActivity)startActivitySync(new Intent(getTargetContext(),QuickSettingsActivity.class).putExtra("volumes",volumes).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();}
    private void close(){if(page!=null){ui(new Runnable(){public void run(){KeypadDialog d=KeypadDialog.current();if(d!=null)d.dismiss();page.finish();}});page=null;}}
    private void shot(String name)throws Exception{SystemClock.sleep(180);Bitmap b=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();check(b!=null,"capture "+name);try(FileOutputStream out=new FileOutputStream(new File(shots,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}}
    private void snapshot(String group){prefs.put(group,new HashMap<String,Object>(getTargetContext().getSharedPreferences(group,0).getAll()));}
    @SuppressWarnings("unchecked") private void restore(){for(Map.Entry<String,Map<String,?>> group:prefs.entrySet()){SharedPreferences.Editor e=getTargetContext().getSharedPreferences(group.getKey(),0).edit().clear();for(Map.Entry<String,?> row:group.getValue().entrySet()){String k=row.getKey();Object v=row.getValue();if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Set)e.putStringSet(k,(Set<String>)v);else e.putString(k,(String)v);}if(!e.commit())throw new AssertionError("restore "+group.getKey());}}
    private void controls()throws Exception{
        open(false);QuickSettingsPanel original=page.panel;
        check(page.getCurrentFocus()==original,"panel owns keypad focus");
        AccessibilityNodeProvider nodes=original.getAccessibilityNodeProvider();
        for(int i=0;i<7;i++){AccessibilityNodeInfo info=nodes.createAccessibilityNodeInfo(i);check(info!=null&&info.isVisibleToUser()&&info.getContentDescription().length()>0,"visible labelled control "+i);if(i>=5)check(info.getRangeInfo()!=null,"slider exposes range "+i);info.recycle();}
        shot("quick-settings");
        key(KeyEvent.KEYCODE_DPAD_RIGHT);check(original.selected()==1,"right chooses Bluetooth");key(KeyEvent.KEYCODE_DPAD_DOWN);check(original.selected()==4,"down chooses profile");
        key(KeyEvent.KEYCODE_DPAD_CENTER);check(KeypadDialog.current()!=null,"profile uses shared picker");key(KeyEvent.KEYCODE_BACK);check(page.panel==original&&original.selected()==4,"profile cancellation preserves tile and panel");
        key(KeyEvent.KEYCODE_DPAD_DOWN);check(original.selected()==5,"profile down chooses brightness");key(KeyEvent.KEYCODE_DPAD_UP);check(original.selected()==4,"up returns to previous tile");
        select(5);key(KeyEvent.KEYCODE_DPAD_CENTER);KeypadDialog levels=KeypadDialog.current();check(levels!=null&&(Integer)field(levels,"selected")==Math.max(0,Math.round(original.state().brightnessPercent()/5f)-1),"exact brightness picker starts at current level");key(KeyEvent.KEYCODE_BACK);
        int[] other={AudioManager.STREAM_RING,AudioManager.STREAM_ALARM,AudioManager.STREAM_VOICE_CALL};int[] before=new int[other.length];for(int i=0;i<other.length;i++)before[i]=audio.getStreamVolume(other[i]);
        int b=original.state().brightnessPercent();int direction=b>=95?KeyEvent.KEYCODE_DPAD_LEFT:KeyEvent.KEYCODE_DPAD_RIGHT;key(direction);check(Math.abs(original.state().brightnessPercent()-b)==5,"left/right writes brightness by five percent");check(Settings.System.getInt(getTargetContext().getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE)==0,"slider selects manual brightness");
        select(6);int v=audio.getStreamVolume(AudioManager.STREAM_MUSIC);key(v==audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?KeyEvent.KEYCODE_DPAD_LEFT:KeyEvent.KEYCODE_DPAD_RIGHT);check(Math.abs(audio.getStreamVolume(AudioManager.STREAM_MUSIC)-v)==1,"media slider changes music by one step");for(int i=0;i<other.length;i++)check(before[i]==audio.getStreamVolume(other[i]),"other stream remains unchanged "+other[i]);
        shot("media-slider");
        ui(new Runnable(){public void run(){Settings.System.putInt(page.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,102);audio.setStreamVolume(AudioManager.STREAM_MUSIC,media,0);}});SystemClock.sleep(220);waitForIdleSync();check(page.panel==original&&original.selected()==6&&original.state().brightness==102&&original.state().media==media,"external changes refresh values without replacing focus");
        key(KeyEvent.KEYCODE_MENU);check(KeypadDialog.current()!=null,"left softkey opens Options");for(int i=0;i<5;i++)key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER);check((Boolean)field(page,"volumePage"),"Options retains all-stream volumes");key(KeyEvent.KEYCODE_BACK);check(page.panel.selected()==6,"all-stream Back restores selected slider");
        close();open(true);check((Boolean)field(page,"volumePage"),"existing volumes intent opens all four streams");key(KeyEvent.KEYCODE_BACK);check(page.panel!=null,"volumes route returns to modern controls");close();
    }
    private void rendering()throws Exception{
        open(false);final QuickSettingsControls.State live=page.controls.read();
        ui(new Runnable(){public void run(){
            for(int theme=0;theme<Appearance.COLORS.length;theme++)for(float scale:new float[]{1f,1.15f,1.3f}){
                QuickSettingsPanel fixture=new QuickSettingsPanel(page,new QuickSettingsPanel.Actions(){public void activate(int i){}public void value(int i,int v){}public void focused(int i){}},0);fixture.style(new Appearance.Palette(Appearance.COLORS[theme]),scale);fixture.state(live);
                fixture.measure(View.MeasureSpec.makeMeasureSpec(240,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));fixture.layout(0,0,240,fixture.getMeasuredHeight());Bitmap bitmap=Bitmap.createBitmap(240,fixture.getMeasuredHeight(),Bitmap.Config.ARGB_8888);fixture.draw(new Canvas(bitmap));bitmap.recycle();
                check(fixture.bounds[6].bottom<=fixture.getMeasuredHeight()&&fixture.bounds[5].bottom<fixture.bounds[6].top,"nonoverlapping reachable geometry "+theme+"/"+scale);
            }
            page.getSharedPreferences("accessibility",0).edit().putInt("text",2).commit();page.onResume();page.panel.select(6);
        }});SystemClock.sleep(220);waitForIdleSync();AccessibilityNodeInfo mediaNode=page.panel.getAccessibilityNodeProvider().createAccessibilityNodeInfo(6);check(mediaNode.isVisibleToUser(),"large text scrolls to selected media slider");mediaNode.recycle();shot("large-text");
        ui(new Runnable(){public void run(){page.getSharedPreferences("appearance",0).edit().putInt("theme",2).commit();page.getSharedPreferences("accessibility",0).edit().putInt("text",0).commit();page.onResume();page.panel.select(0);}});check(page.panel.selected()==0,"theme changes retain a usable panel");shot("ocean-theme");
        ui(new Runnable(){public void run(){page.getSharedPreferences("accessibility",0).edit().putBoolean("contrast",true).commit();page.onResume();}});shot("high-contrast");close();
    }
    public void onCreate(Bundle args){start();}
    public void onStart(){Bundle result=new Bundle();try{
        getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);Context c=getTargetContext();snapshot("appearance");snapshot("accessibility");
        brightness=Settings.System.getInt(c.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS);mode=Settings.System.getInt(c.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE);audio=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);media=audio.getStreamVolume(AudioManager.STREAM_MUSIC);settingsSaved=true;
        awake=((PowerManager)c.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"keyshell:quick-settings-checks");awake.acquire(180000);
        ui(new Runnable(){public void run(){c.getSharedPreferences("appearance",0).edit().putInt("theme",17).commit();c.getSharedPreferences("accessibility",0).edit().putInt("text",0).putBoolean("contrast",false).commit();}});shots=new File(c.getFilesDir(),"quick-settings-shots");shots.mkdirs();controls();rendering();result.putString("result","PASS "+checks+" quick settings checks; radios/profile untouched; brightness/media restored");
    }catch(Throwable error){result.putString("error","after "+checks+" checks: "+android.util.Log.getStackTraceString(error));}finally{try{close();restore();if(settingsSaved){Settings.System.putInt(getTargetContext().getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,brightness);Settings.System.putInt(getTargetContext().getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,mode);audio.setStreamVolume(AudioManager.STREAM_MUSIC,media,0);}if(awake!=null&&awake.isHeld())awake.release();}catch(Throwable error){result.putString("restore_error",android.util.Log.getStackTraceString(error));}}
        try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"quick-settings-result.txt"))){out.write(result.toString().getBytes("UTF-8"));}catch(IOException ignored){}
        finish(result.containsKey("error")||result.containsKey("restore_error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}
