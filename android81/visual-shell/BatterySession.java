package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.*;import android.os.*;
/** Two real snapshots, no wake lock or polling that would disturb standby. */
final class BatterySession {
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("battery-session",0);}
    private static Intent battery(Context c){return c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));}
    static boolean active(Context c){return prefs(c).getBoolean("active",false);}
    static void start(Context c){Intent b=battery(c);if(b==null||b.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0)throw new IllegalStateException(L.t("Deconectează întâi încărcătorul"));prefs(c).edit().putBoolean("active",true).putBoolean("interrupted",false).putLong("awake",SystemClock.uptimeMillis()).putLong("start",SystemClock.elapsedRealtime()).putInt("level",level(b)).putInt("counter",counter(c)).commit();}
    static void observe(Context c,Intent b){if(!active(c))return;if(b.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0||SystemClock.elapsedRealtime()<prefs(c).getLong("start",0))prefs(c).edit().putBoolean("interrupted",true).commit();}
    static void interrupt(Context c){if(active(c))prefs(c).edit().putBoolean("interrupted",true).commit();}
    private static int level(Intent b){return b.getIntExtra(BatteryManager.EXTRA_LEVEL,0)*100/Math.max(1,b.getIntExtra(BatteryManager.EXTRA_SCALE,100));}
    private static int counter(Context c){return ((BatteryManager)c.getSystemService(Context.BATTERY_SERVICE)).getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);}
    static String text(Context c){SharedPreferences p=prefs(c);return active(c)?L.t("Măsurare activă · ")+Math.max(0,(SystemClock.elapsedRealtime()-p.getLong("start",0))/60000)+" min":p.getString("result",L.t("Nicio probă salvată"));}
    static void stop(Context c){SharedPreferences p=prefs(c);Intent b=battery(c);if(!active(c)||b==null)return;observe(c,b);long elapsed=SystemClock.elapsedRealtime()-p.getLong("start",0);String result;if(elapsed<0||p.getBoolean("interrupted",false))result=L.t("Probă întreruptă · încărcare sau repornire");else{int delta=p.getInt("level",0)-level(b),initial=p.getInt("counter",Integer.MIN_VALUE),end=counter(c);result=L.t("Probă · ")+(elapsed/60000)+L.t(" min · scădere ")+delta+"%";if(initial!=Integer.MIN_VALUE&&end!=Integer.MIN_VALUE&&initial>=end)result+=" · "+(initial-end)+L.t(" µAh raportați");long awake=Math.max(0,SystemClock.uptimeMillis()-p.getLong("awake",SystemClock.uptimeMillis()));if(p.contains("awake"))result+=L.t(" · activ ")+(awake/60000)+" min / repaus "+(Math.max(0,elapsed-awake)/60000)+" min";if(elapsed<300000)result+=L.t(" · prea scurtă pentru autonomie");else if(delta==0)result+=L.t(" · sub rezoluția procentuală");}p.edit().putBoolean("active",false).putString("result",result).commit();}
}
