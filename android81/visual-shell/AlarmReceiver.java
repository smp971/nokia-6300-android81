package ro.leo.visual;

import android.content.*;
import org.json.JSONObject;

public final class AlarmReceiver extends BroadcastReceiver {
    public void onReceive(Context context,Intent intent) {
        try {
            String action=intent.getAction();
            if(Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_TIME_CHANGED.equals(action) || Intent.ACTION_TIMEZONE_CHANGED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
                AlarmScheduler.restore(context);CalendarReminders.schedule(context); return;
            }
            if(!AlarmScheduler.FIRE.equals(action)) return;
            String kind=intent.getStringExtra("kind"),label="Temporizator";
            if("timer".equals(kind)) {
                if(!AlarmScheduler.state(context).getBoolean("timer",false)) return;
                AlarmScheduler.state(context).edit().putBoolean("timer",false).commit();
            } else if("alarm".equals(kind)) {
                int id=intent.getIntExtra("id",-1); JSONObject item=AlarmScheduler.get(context,id);
                if(item==null || !item.optBoolean("enabled")) return;
                label=item.optString("label","Alarmă");
                AlarmScheduler.save(context,id,item.getInt("hour"),item.getInt("minute"),label,item.getBoolean("daily"),item.getBoolean("daily"));
            } else return;
            context.startForegroundService(new Intent(context,AlarmRingService.class).putExtra("label",label));
        } catch(Exception failure) { android.util.Log.e("LeoAlarm","Could not schedule or start alarm"); }
    }
}
