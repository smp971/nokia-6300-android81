package ro.leo.visual;
import android.app.*;import android.content.*;import android.os.*;import android.provider.Settings;import java.util.*;
/** Run only while host explicitly simulates battery unplug, then resets battery service. */
public final class BatteryTests extends Instrumentation {
    public void onCreate(Bundle b){start();}
    private void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    public void onStart(){Context c=getTargetContext();Bundle out=new Bundle();String low=Settings.Global.getString(c.getContentResolver(),"low_power");SharedPreferences p=c.getSharedPreferences("battery-session",0);Map<String,?> before=p.getAll();try{
        PowerManager power=(PowerManager)c.getSystemService(Context.POWER_SERVICE);Settings.Global.putInt(c.getContentResolver(),"low_power",1);for(int i=0;i<40&&!power.isPowerSaveMode();i++)SystemClock.sleep(100);check(power.isPowerSaveMode(),"actual PowerManager saver under simulated unplug");
        BatterySession.start(c);check(BatterySession.active(c),"sample started");BatterySession.interrupt(c);BatterySession.stop(c);check(!BatterySession.active(c)&&BatterySession.text(c).contains("întreruptă"),"interrupted sample invalidated");
        out.putString("result","PASS PowerManager saver and interrupted sample guard under SIMULATED unplug; no physical standby consumption measured");
    }catch(Throwable e){out.putString("error",android.util.Log.getStackTraceString(e));}finally{Settings.Global.putString(c.getContentResolver(),"low_power",low);SharedPreferences.Editor edit=p.edit().clear();for(Map.Entry<String,?> e:before.entrySet()){Object v=e.getValue();if(v instanceof String)edit.putString(e.getKey(),(String)v);else if(v instanceof Boolean)edit.putBoolean(e.getKey(),(Boolean)v);else if(v instanceof Long)edit.putLong(e.getKey(),(Long)v);else if(v instanceof Integer)edit.putInt(e.getKey(),(Integer)v);}edit.commit();}finish(out.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,out);}
}
