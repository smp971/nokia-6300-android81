package ro.leo.visual;
import ro.leo.i18n.L;

import android.content.*;
import org.json.JSONObject;

public final class AlarmReceiver extends BroadcastReceiver {
    public void onReceive(Context context,Intent intent) {
        try {
            String action=intent.getAction();
            if(Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_TIME_CHANGED.equals(action) || Intent.ACTION_TIMEZONE_CHANGED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
                AlarmScheduler.restore(context);CalendarReminders.schedule(context);try{SoundSchedule.sync(context);}catch(RuntimeException e){FaultLedger.record(context,"SoundSchedule",e);}return;
            }
            if(!AlarmScheduler.FIRE.equals(action)) return;
            String kind=intent.getStringExtra("kind"),label=L.t("Temporizator");int ringingId=-1;
            if("timer".equals(kind)) {
                if(!AlarmScheduler.state(context).getBoolean("timer",false)) return;
                AlarmScheduler.state(context).edit().putBoolean("timer",false).commit();
            } else if("snooze".equals(kind)) {
                int id=intent.getIntExtra("id",-1);JSONObject item=AlarmScheduler.get(context,id);long due=AlarmScheduler.state(context).getLong("snooze"+id,0);if(item==null||due<=0||System.currentTimeMillis()<due-1000||System.currentTimeMillis()-due>3600000)return;AlarmScheduler.cancelSnooze(context,id);label=item.optString("label",L.t("Alarmă"));ringingId=id;
            } else if("alarm".equals(kind)) {
                int id=intent.getIntExtra("id",-1); JSONObject item=AlarmScheduler.get(context,id);
                if(item==null || !item.optBoolean("enabled")) return;
                label=item.optString("label",L.t("Alarmă"));ringingId=id;
                AlarmScheduler.saveDays(context,id,item.getInt("hour"),item.getInt("minute"),label,AlarmScheduler.days(item),AlarmScheduler.days(item)!=0);
            } else return;
            context.startForegroundService(new Intent(context,AlarmRingService.class).putExtra("label",label).putExtra("alarm_id",ringingId));
        } catch(Exception failure) { android.util.Log.e("LeoAlarm","Could not schedule or start alarm"); }
    }
}
