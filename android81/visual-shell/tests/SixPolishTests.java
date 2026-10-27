package ro.leo.visual;
import android.app.*;import android.content.*;import android.os.*;import android.provider.CallLog;import android.provider.Telephony;import android.view.*;import android.widget.*;import org.json.*;import java.util.*;import java.io.*;import java.lang.reflect.*;
/** Read-only snapshots and local UI; never sends messages, places calls or restores user data. */
public final class SixPolishTests extends Instrumentation {
    int checks;Activity activity;final ArrayList<String> fixtureNames=new ArrayList<String>();void check(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}
    public void onCreate(Bundle b){start();}
    public void onStart(){Bundle out=new Bundle();File archive=null;final SharedPreferences settingsPrefs=getTargetContext().getSharedPreferences("settings-ui",0);final boolean hadCategory=settingsPrefs.contains("category");final int savedCategory=settingsPrefs.getInt("category",0);try{
        Context c=getTargetContext();
        check(HistoryFilter.matches("0123","Ștefan",CallLog.Calls.MISSED_TYPE,1,"stefan",1,true),"history combines name/SIM/missed filters");
        check(!HistoryFilter.matches("0123","Ștefan",CallLog.Calls.MISSED_TYPE,0,"stefan",1,true),"other SIM excluded");
        check(!HistoryFilter.matches("0123","Ștefan",CallLog.Calls.OUTGOING_TYPE,1,"",1,true),"outgoing excluded by missed filter");
        check(HistoryFilter.matches("0123","A",CallLog.Calls.INCOMING_TYPE,0,"123",-1,false),"history number search");
        check("😀".equals(TextRange.select("a😀ș",1,2)),"selection preserves emoji");
        boolean bad=false;try{TextRange.select("abc",2,1);}catch(IllegalArgumentException e){bad=true;}check(bad,"reversed range rejected");
        check("✓✓".equals(MessagesActivity.statusMark(Telephony.Sms.MESSAGE_TYPE_SENT,1)),"delivered marker");
        check("!".equals(MessagesActivity.statusMark(Telephony.Sms.MESSAGE_TYPE_SENT,2)),"failed delivery marker");
        check(KeypadTabs.next(0,-1,3)==2&&KeypadTabs.next(2,1,3)==0,"tabs wrap both directions");
        JSONArray alarms=new JSONArray().put(new JSONObject().put("id",1).put("hour",10).put("minute",30).put("days",127).put("label","Fixture").put("enabled",false));AlarmArchive.validate(alarms,2);check(true,"valid disabled alarm archive");
        bad=false;try{AlarmArchive.validate(new JSONArray(alarms.toString()).put(alarms.get(0)),2);}catch(Exception e){bad=true;}check(bad,"duplicate alarm IDs rejected");
        bad=false;try{AlarmArchive.validate(alarms,1);}catch(Exception e){bad=true;}check(bad,"next ID collision rejected");
        bad=false;try{JSONArray invalid=new JSONArray(alarms.toString());invalid.getJSONObject(0).put("minute",60);AlarmArchive.validate(invalid,2);}catch(Exception e){bad=true;}check(bad,"invalid alarm time rejected");
        JSONArray named=new JSONArray(alarms.toString());named.getJSONObject(0).put("label",String.format("%0100d",0));AlarmArchive.validate(named,2);check(true,"existing long alarm labels remain supported");
        JSONObject snapshot=BackupEngine.snapshot(c);BackupEngine.validate(c,snapshot);
        final String prefix="polish-fixture-"+SystemClock.elapsedRealtime()+"-";
        Context fixture=new ContextWrapper(c){public SharedPreferences getSharedPreferences(String name,int mode){String key=prefix+name;if(!fixtureNames.contains(key))fixtureNames.add(key);return super.getSharedPreferences(key,mode);}};
        JSONObject fixtureGroups=new JSONObject();fixtureGroups.put("appearance",new JSONObject().put("app-order",new JSONObject().put("type","string").put("value","6,0,2")));
        fixtureGroups.put("alarms",new JSONObject().put("items",new JSONObject().put("type","string").put("value",alarms.toString())).put("next",new JSONObject().put("type","int").put("value",2)));
        BackupEngine.applyPreferences(fixture,fixtureGroups);check("6,0,2".equals(fixture.getSharedPreferences("appearance",0).getString("app-order","")),"menu order restored into isolated preferences");check(new JSONArray(fixture.getSharedPreferences("alarms",0).getString("items","[]")).length()==1,"alarm definitions restored into isolated preferences");
        fixtureGroups.getJSONObject("appearance").remove("app-order");BackupEngine.applyPreferences(fixture,fixtureGroups);check(!fixture.getSharedPreferences("appearance",0).contains("app-order"),"restoration removes obsolete supported keys");

        JSONObject prefs=snapshot.getJSONObject("preferences");check(prefs.has("alarms")&&prefs.has("camera")&&prefs.has("sms-options")&&prefs.has("leo-language"),"new backup groups included");
        JSONObject changed=new JSONObject(snapshot.toString());changed.getJSONObject("preferences").getJSONObject("appearance").put("theme",new JSONObject().put("type","int").put("value",0));
        JSONObject chosen=BackupEngine.selected(changed,snapshot,new HashSet<String>(Arrays.asList("appearance")));
        check(!chosen.getJSONObject("preferences").has("alarms"),"unselected alarms are not reapplied");
        JSONObject old=new JSONObject(snapshot.toString());old.getJSONObject("preferences").remove("alarms");old.getJSONObject("preferences").remove("camera");old.getJSONObject("preferences").remove("sms-options");old.getJSONObject("preferences").remove("leo-language");BackupEngine.validate(c,old);check(true,"older backups remain valid");
        archive=new File(c.getCacheDir(),"six-polish-backup.json");BackupEngine.write(archive,BackupEngine.archive(snapshot));JSONObject read=BackupEngine.read(c,archive);check(read.getJSONObject("preferences").has("alarms"),"backup archive roundtrip");
        JSONObject envelope=new JSONObject(new String(BackupEngine.bytes(archive,BackupEngine.MAX),"UTF-8"));envelope.put("sha256","invalid");BackupEngine.write(archive,envelope.toString().getBytes("UTF-8"));bad=false;try{BackupEngine.read(c,archive);}catch(Exception e){bad=true;}check(bad,"altered backup rejected before restoration");
        activity=startActivitySync(new Intent(c,TextSelectionActivity.class).putExtra("text","a😀ș").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));check(activity!=null,"keypad selection editor opens");close();
        activity=startActivitySync(new Intent(c,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final Activity settings=activity;Field category=SettingsActivity.class.getDeclaredField("category");category.setAccessible(true);final int initialCategory=category.getInt(settings);runOnMainSync(new Runnable(){public void run(){settings.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);setInTouchMode(true);}});
        Field root=LeoActivity.class.getDeclaredField("root");root.setAccessible(true);final LinearLayout layout=(LinearLayout)root.get(settings);final View tab=((LinearLayout)layout.getChildAt(1)).getChildAt(1);
        final boolean[] focused={false};runOnMainSync(new Runnable(){public void run(){focused[0]=tab.requestFocus();}});check(focused[0],"settings tab receives touch-mode focus");
        runOnMainSync(new Runnable(){public void run(){settings.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));settings.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_DOWN));}});check(settings.getCurrentFocus() instanceof ListView,"down returns from settings tabs to list");
        final View next=((LinearLayout)layout.getChildAt(1)).getChildAt(2);for(int i=0;i<30&&next.getWidth()==0;i++)SystemClock.sleep(100);runOnMainSync(new Runnable(){public void run(){long now=SystemClock.uptimeMillis();next.dispatchTouchEvent(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,next.getWidth()/2f,next.getHeight()/2f,0));next.dispatchTouchEvent(MotionEvent.obtain(now,now+10,MotionEvent.ACTION_UP,next.getWidth()/2f,next.getHeight()/2f,0));}});check(category.getInt(settings)==KeypadTabs.next(initialCategory,1,SettingsActivity.TABS.length),"one pointer click changes settings tab in touch mode");
        out.putString("result","PASS "+checks+" polish contracts; user data not restored or replaced");
    }catch(Throwable e){out.putString("error",android.util.Log.getStackTraceString(e));}finally{close();if(hadCategory)settingsPrefs.edit().putInt("category",savedCategory).commit();else settingsPrefs.edit().remove("category").commit();for(String key:fixtureNames)getTargetContext().deleteSharedPreferences(key);setInTouchMode(false);if(archive!=null)new android.util.AtomicFile(archive).delete();}
    finish(out.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,out);}
    void close(){if(activity!=null){final Activity a=activity;runOnMainSync(new Runnable(){public void run(){a.finish();}});activity=null;waitForIdleSync();}}
}
