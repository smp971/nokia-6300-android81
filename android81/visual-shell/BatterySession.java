package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.*;import android.os.*;
/** Event-driven real battery snapshots; no polling, alarms or wake lock. */
final class BatterySession {
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("battery-session",0);}
    private static Intent battery(Context c){return c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));}
    static boolean active(Context c){return prefs(c).getBoolean("active",false);}
    static boolean pending(Context c){return prefs(c).getBoolean("armed",false);}
    static void arm(Context c){if(active(c))throw new IllegalStateException(L.t("O probă este deja activă"));prefs(c).edit().putBoolean("armed",true).commit();Intent b=battery(c);if(b!=null)observe(c,b);}
    static void cancel(Context c){prefs(c).edit().putBoolean("armed",false).commit();}
    static void start(Context c){Intent b=battery(c);if(b==null||b.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0)throw new IllegalStateException(L.t("Deconectează întâi încărcătorul"));begin(c,b,false);}
    private static void begin(Context c,Intent b,boolean automatic){prefs(c).edit().putBoolean("active",true).putBoolean("armed",false).putBoolean("automatic",automatic).putBoolean("interrupted",false).putString("boot",BootReadiness.bootId()).putLong("awake",SystemClock.uptimeMillis()).putLong("start",SystemClock.elapsedRealtime()).putLong("started-wall",System.currentTimeMillis()).putInt("level",level(b)).putInt("counter",counter(c)).commit();}
    static void observe(Context c,Intent b){
        if(pending(c)&&b.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)==0)begin(c,b,true);
        if(!active(c))return;SharedPreferences p=prefs(c);
        if(SystemClock.elapsedRealtime()<p.getLong("start",0)||!p.getString("boot",BootReadiness.bootId()).equals(BootReadiness.bootId()))p.edit().putBoolean("interrupted",true).commit();
        if(b.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0){if(p.getBoolean("automatic",false))finish(c,b);else p.edit().putBoolean("interrupted",true).commit();}
    }
    static void interrupt(Context c){SharedPreferences.Editor e=prefs(c).edit().putBoolean("armed",false);if(active(c))e.putBoolean("interrupted",true);e.commit();}
    private static int level(Intent b){return b.getIntExtra(BatteryManager.EXTRA_LEVEL,0)*100/Math.max(1,b.getIntExtra(BatteryManager.EXTRA_SCALE,100));}
    private static int counter(Context c){return ((BatteryManager)c.getSystemService(Context.BATTERY_SERVICE)).getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);}
    static String text(Context c){SharedPreferences p=prefs(c);return pending(c)?L.t("Probă pregătită · scoate USB"):active(c)?L.t("Măsurare activă · ")+Math.max(0,(SystemClock.elapsedRealtime()-p.getLong("start",0))/60000)+" min":p.getString("result",L.t("Nicio probă salvată"));}
    static void stop(Context c){if(pending(c)){cancel(c);return;}Intent b=battery(c);if(!active(c)||b==null)return;if(b.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0&&!prefs(c).getBoolean("automatic",false))prefs(c).edit().putBoolean("interrupted",true).commit();finish(c,b);}
    private static void finish(Context c,Intent b){
        SharedPreferences p=prefs(c);long elapsed=SystemClock.elapsedRealtime()-p.getLong("start",0),awake=Math.max(0,SystemClock.uptimeMillis()-p.getLong("awake",SystemClock.uptimeMillis()));
        int endLevel=level(b),delta=p.getInt("level",0)-endLevel,initial=p.getInt("counter",Integer.MIN_VALUE),end=counter(c);
        boolean invalid=elapsed<0||p.getBoolean("interrupted",false)||!p.getString("boot",BootReadiness.bootId()).equals(BootReadiness.bootId());
        String result=invalid?L.t("Probă întreruptă · încărcare sau repornire"):L.t("Probă · ")+(elapsed/60000)+L.t(" min · scădere ")+delta+"%";
        if(!invalid){
            if(initial>0&&end>0&&initial>end)result+=" · "+(initial-end)+L.t(" µAh raportați");
            else result+=L.t(" · contor fără variație utilă");
            result+=L.t(" · activ ")+(awake/60000)+L.t(" min / repaus ")+(Math.max(0,elapsed-awake)/60000)+" min";
            if(elapsed<300000)result+=L.t(" · prea scurtă pentru autonomie");else if(delta==0)result+=L.t(" · sub rezoluția procentuală");
        }
        p.edit().putBoolean("active",false).putBoolean("armed",false).putString("result",result).putLong("elapsed-ms",Math.max(0,elapsed)).putLong("awake-ms",awake).putLong("ended-wall",System.currentTimeMillis()).putInt("end-level",endLevel).putInt("end-counter",end).putBoolean("valid",!invalid).putInt("plugged-on-end",b.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)).commit();
    }
}
