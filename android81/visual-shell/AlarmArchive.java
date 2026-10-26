package ro.leo.visual;
import android.content.*;import org.json.*;import java.util.*;
/** Validated alarm definitions only; active countdowns and snoozes are not restored. */
final class AlarmArchive {
    static void validate(JSONArray alarms,int next)throws Exception{
        if(alarms.length()>32||next<1||next>1000000)throw new IllegalArgumentException("Invalid alarm archive");
        Set<Integer> ids=new HashSet<Integer>();
        for(int i=0;i<alarms.length();i++){
            JSONObject a=alarms.getJSONObject(i);
            for(String key:new String[]{"id","hour","minute"})if(!(a.get(key) instanceof Integer))throw new IllegalArgumentException("Invalid alarm number");
            int id=a.getInt("id"),hour=a.getInt("hour"),minute=a.getInt("minute");
            if(id<1||id>=next||!ids.add(id)||hour<0||hour>23||minute<0||minute>59||!(a.get("enabled") instanceof Boolean)||!(a.get("label") instanceof String)||a.getString("label").length()>4096)throw new IllegalArgumentException("Invalid alarm definition");
            if(a.has("days")&&(!(a.get("days") instanceof Integer)||a.getInt("days")<0||a.getInt("days")>127))throw new IllegalArgumentException("Invalid alarm days");
            if(a.has("daily")&&!(a.get("daily") instanceof Boolean))throw new IllegalArgumentException("Invalid daily flag");
        }
    }
    static void reschedule(Context c,JSONArray previous)throws Exception{
        for(int i=0;i<previous.length();i++){int id=previous.getJSONObject(i).getInt("id");AlarmScheduler.manager(c).cancel(AlarmScheduler.pending(c,"alarm",id));AlarmScheduler.cancelSnooze(c,id);}
        JSONArray now=AlarmScheduler.alarms(c);
        for(int i=0;i<now.length();i++){JSONObject a=now.getJSONObject(i);if(a.getBoolean("enabled"))AlarmScheduler.arm(c,a);}
    }
}
