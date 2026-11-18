package ro.leo.visual;

import android.app.Instrumentation;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Isolated local records and battery transitions; never changes Android's battery service. */
public final class BrowserStateDeviceTests extends Instrumentation {
    private int checks;private LeoActivity page;
    private void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    private void ui(Runnable task){runOnMainSync(task);waitForIdleSync();}
    private ListView list(View view){if(view instanceof ListView)return (ListView)view;if(view instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)view;for(int i=0;i<g.getChildCount();i++){ListView found=list(g.getChildAt(i));if(found!=null)return found;}}return null;}
    private void restore(SharedPreferences p,Map<String,?> values){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> item:values.entrySet()){Object v=item.getValue();if(v instanceof String)e.putString(item.getKey(),(String)v);else if(v instanceof Integer)e.putInt(item.getKey(),(Integer)v);else if(v instanceof Long)e.putLong(item.getKey(),(Long)v);else if(v instanceof Boolean)e.putBoolean(item.getKey(),(Boolean)v);else if(v instanceof Float)e.putFloat(item.getKey(),(Float)v);}e.commit();}
    private void battery(){
        final Context real=getTargetContext();final Intent sample=new Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL,87).putExtra(BatteryManager.EXTRA_SCALE,100).putExtra(BatteryManager.EXTRA_PLUGGED,2);
        Context fake=new ContextWrapper(real){public SharedPreferences getSharedPreferences(String n,int m){return real.getSharedPreferences("battery-session-fixture",0);}public Intent registerReceiver(BroadcastReceiver r,IntentFilter f){return new Intent(sample);}};
        SharedPreferences p=fake.getSharedPreferences("battery-session",0);p.edit().clear().commit();
        try{
            BatterySession.arm(fake);check(BatterySession.pending(fake)&&!BatterySession.active(fake),"USB arms without starting or faking discharge");
            sample.putExtra(BatteryManager.EXTRA_PLUGGED,0);BatterySession.observe(fake,sample);check(BatterySession.active(fake)&&!BatterySession.pending(fake),"actual unplug event starts armed session");
            sample.putExtra(BatteryManager.EXTRA_LEVEL,86).putExtra(BatteryManager.EXTRA_PLUGGED,2);BatterySession.observe(fake,sample);
            check(!BatterySession.active(fake)&&p.getBoolean("valid",false),"reconnect saves automatic sample");check(BatterySession.text(fake).contains("1%"),"real snapshot level delta is reported");check(p.getInt("end-level",0)==86&&p.getInt("plugged-on-end",0)==2,"structured end sample retained");
            sample.putExtra(BatteryManager.EXTRA_PLUGGED,0);BatterySession.start(fake);sample.putExtra(BatteryManager.EXTRA_PLUGGED,2);BatterySession.observe(fake,sample);BatterySession.stop(fake);
            check(!p.getBoolean("valid",true),"legacy manual sessions still reject charging interruption");
            BatterySession.arm(fake);BatterySession.cancel(fake);check(!BatterySession.pending(fake),"prepared session can be canceled");
            sample.putExtra(BatteryManager.EXTRA_PLUGGED,0);BatterySession.start(fake);p.edit().putString("boot","different-boot").commit();BatterySession.stop(fake);check(!p.getBoolean("valid",true),"reboot invalidates clocks");
        }finally{real.deleteSharedPreferences("battery-session-fixture");}
    }
    private void keyRecovery(){
        Context c=getTargetContext();String key="enabled_accessibility_services";String original=android.provider.Settings.Secure.getString(c.getContentResolver(),key);int enabled=android.provider.Settings.Secure.getInt(c.getContentResolver(),"accessibility_enabled",0);
        check(original!=null&&original.contains("LeoPointerService"),"key service starts from its existing grant");
        SharedPreferences journal=c.getSharedPreferences("key-service-repair",0);Map<String,?> before=journal.getAll();ArrayList<String> others=new ArrayList<>();for(String item:original.split(":"))if(!item.endsWith("/.LeoPointerService")&&!item.endsWith("/ro.leo.visual.LeoPointerService"))others.add(item);String without=android.text.TextUtils.join(":",others);
        try{
            journal.edit().putString("before",original).putString("without",without).putInt("enabled",enabled).commit();android.provider.Settings.Secure.putString(c.getContentResolver(),key,without);if(without.isEmpty())android.provider.Settings.Secure.putInt(c.getContentResolver(),"accessibility_enabled",0);
            LeoApplication.recoverKeys(c);
            check(original.equals(android.provider.Settings.Secure.getString(c.getContentResolver(),key)),"interrupted key repair restores exact grant list");
            check(android.provider.Settings.Secure.getInt(c.getContentResolver(),"accessibility_enabled",0)==enabled,"interrupted key repair restores enabled flag");check(!journal.contains("before"),"completed transaction journal cleared");
        }finally{android.provider.Settings.Secure.putString(c.getContentResolver(),key,original);android.provider.Settings.Secure.putInt(c.getContentResolver(),"accessibility_enabled",enabled);restore(journal,before);}
    }
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){Bundle out=new Bundle();int result=0;Context c=getTargetContext();SharedPreferences prefs=c.getSharedPreferences("browser",0);Map<String,?> before=prefs.getAll();try{
        keyRecovery();check(!c.getPackageManager().getProviderInfo(new ComponentName(c,BrowserStateProvider.class),0).exported,"store is private");
        prefs.edit().putString("bookmarks","[{\"url\":\"https://example.org/legacy\",\"title\":\"Vechi\"}]").commit();
        check(BrowserStore.records(c,"bookmarks").length()==1,"old text-reader bookmark schema retained");
        BrowserStore.remember(c,"bookmarks","https://example.org/legacy","Nou");check(BrowserStore.records(c,"bookmarks").length()==1&&BrowserStore.records(c,"bookmarks").getJSONObject(0).getString("title").equals("Nou"),"same URL updates without duplicates");
        BrowserStore.clear(c,"history");for(int i=0;i<55;i++)BrowserStore.remember(c,"history","https://example.org/"+i,"Test "+i);
        check(BrowserStore.records(c,"history").length()==50,"history capped at fifty entries");check(BrowserStore.records(c,"history").getJSONObject(0).getString("url").endsWith("/54"),"newest entry first");
        BrowserStore.remove(c,"history","https://example.org/54");check(BrowserStore.records(c,"history").length()==49,"remove affects selected entry");
        check(BrowserStore.clean("[{\"url\":\"file:///private\"},null]",30).length()==0,"unsafe and corrupt records filtered");
        try{BrowserStore.remember(c,"bookmarks","javascript:alert(1)","invalid");check(false,"unsafe write rejected");}catch(IllegalArgumentException expected){checks++;}
        BrowserStore.setting(c,"cursor-speed",2);check(BrowserStore.snapshot(c).getInt("cursor-speed")==2,"cursor speed persists");try{BrowserStore.setting(c,"cursor-speed",7);check(false,"bad speed rejected");}catch(IllegalArgumentException expected){checks++;}
        BrowserStore.resume(c,"https://example.org/legacy","Continuare",0,123);check(BrowserStore.snapshot(c).getInt("last-y")==123,"resume scroll stored");
        page=(LeoActivity)startActivitySync(new Intent(c,BrowserHubActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        final ListView hub=list(page.root);check(hub.getCount()==7,"hub contains address, resume, favorites, history and old engines");
        ui(new Runnable(){public void run(){hub.performItemClick(hub.getChildAt(2),2,hub.getAdapter().getItemId(2));}});check(list(page.root).getCount()==1,"shared favorite opens native list");
        ui(new Runnable(){public void run(){page.onBackPressed();}});check(list(page.root).getCount()==7,"Back returns to hub");
        ui(new Runnable(){public void run(){page.finish();}});page=null;battery();
        out.putString("result","PASS "+checks+" browser state and battery transition checks");result=-1;
    }catch(Throwable error){out.putString("result","FAIL after "+checks+": "+error);out.putString("trace",android.util.Log.getStackTraceString(error));}
    finally{if(page!=null)ui(new Runnable(){public void run(){page.finish();}});restore(prefs,before);}
        finish(result,out);
    }
}
