package ro.leo.visual;

import android.content.*;
import android.os.SystemClock;
import java.io.*;

/** Private boot milestones, distinct from an optical boot-time measurement. */
final class BootReadiness {
    static String bootId() {
        try(BufferedReader in=new BufferedReader(new FileReader("/proc/sys/kernel/random/boot_id"))) {
            String value=in.readLine();return value==null?"unknown":value;
        }catch(IOException unavailable){return "unknown";}
    }
    static void begin(Context context) {
        record(context.getSharedPreferences("boot-readiness",0),bootId(),SystemClock.elapsedRealtime(),false);
    }
    static void healthy(Context context) {
        record(context.getSharedPreferences("boot-readiness",0),bootId(),SystemClock.elapsedRealtime(),true);
    }
    static void record(SharedPreferences p,String boot,long elapsed,boolean healthy) {
        boolean fresh=!boot.equals(p.getString("boot","")) || elapsed<p.getLong("last_elapsed",0);
        SharedPreferences.Editor edit=p.edit().putLong("last_elapsed",elapsed);
        if(fresh)edit.putString("boot",boot).putLong("first_home_ms",elapsed).remove("healthy_ms");
        if(healthy && (fresh || !p.contains("healthy_ms")))edit.putLong("healthy_ms",elapsed);
        edit.apply();
    }
}
