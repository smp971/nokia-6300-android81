package ro.leo.visual;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Settings;
import org.json.*;
import java.util.TimeZone;

final class AlarmScheduler {
    static final String FIRE="ro.leo.visual.ALARM_FIRE";
    static SharedPreferences state(Context c) { return c.getSharedPreferences("alarms",0); }
    static JSONArray alarms(Context c) {
        try { return new JSONArray(state(c).getString("items","[]")); } catch(JSONException invalid) { throw new IllegalStateException("Invalid alarm storage",invalid); }
    }
    static JSONObject get(Context c,int id) {
        JSONArray all=alarms(c); for(int i=0;i<all.length();i++) if(all.optJSONObject(i).optInt("id")==id) return all.optJSONObject(i); return null;
    }
    static void save(Context c,int id,int hour,int minute,String label,boolean daily,boolean enabled) throws JSONException {
        AlarmMath.next(System.currentTimeMillis(),hour,minute,TimeZone.getDefault());
        JSONArray old=alarms(c),next=new JSONArray(); int actual=id<0?state(c).getInt("next",1):id;
        if(id<0 && old.length()>=32) throw new IllegalArgumentException();
        JSONObject item=new JSONObject().put("id",actual).put("hour",hour).put("minute",minute).put("label",label).put("daily",daily).put("enabled",enabled);
        boolean replaced=false;
        for(int i=0;i<old.length();i++) { JSONObject value=old.getJSONObject(i); if(value.getInt("id")==actual) { next.put(item); replaced=true; } else next.put(value); }
        if(!replaced) next.put(item);
        if(!state(c).edit().putString("items",next.toString()).putInt("next",Math.max(state(c).getInt("next",1),actual+1)).commit()) throw new IllegalStateException();
        if(enabled) arm(c,item); else manager(c).cancel(pending(c,"alarm",actual));
    }
    static void delete(Context c,int id) throws JSONException {
        JSONArray next=new JSONArray(),old=alarms(c);
        for(int i=0;i<old.length();i++) if(old.getJSONObject(i).getInt("id")!=id) next.put(old.get(i));
        if(!state(c).edit().putString("items",next.toString()).commit()) throw new IllegalStateException();
        manager(c).cancel(pending(c,"alarm",id));
    }
    static AlarmManager manager(Context c) { return (AlarmManager)c.getSystemService(Context.ALARM_SERVICE); }
    static PendingIntent pending(Context c,String kind,int id) {
        Intent fire=new Intent(c,AlarmReceiver.class).setAction(FIRE).setData(Uri.parse("leo-alarm:"+kind+"/"+id)).putExtra("kind",kind).putExtra("id",id);
        return PendingIntent.getBroadcast(c,id,fire,PendingIntent.FLAG_UPDATE_CURRENT);
    }
    static void arm(Context c,JSONObject item) {
        int id=item.optInt("id"); long when=AlarmMath.next(System.currentTimeMillis(),item.optInt("hour"),item.optInt("minute"),TimeZone.getDefault());
        PendingIntent show=PendingIntent.getActivity(c,100,new Intent(c,AlarmsActivity.class),PendingIntent.FLAG_UPDATE_CURRENT);
        manager(c).setAlarmClock(new AlarmManager.AlarmClockInfo(when,show),pending(c,"alarm",id));
    }
    static void timer(Context c,long duration) {
        int boot=Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);
        if(!state(c).edit().putBoolean("timer",true).putLong("timerElapsed",SystemClock.elapsedRealtime()+duration)
            .putLong("timerWall",System.currentTimeMillis()+duration).putInt("timerBoot",boot).commit()) throw new IllegalStateException();
        manager(c).setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime()+duration,pending(c,"timer",0));
    }
    static long remaining(Context c) {
        if(!state(c).getBoolean("timer",false)) return 0;
        int boot=Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);
        return Math.max(0,boot>=0 && state(c).getInt("timerBoot",-2)==boot?state(c).getLong("timerElapsed",0)-SystemClock.elapsedRealtime():state(c).getLong("timerWall",0)-System.currentTimeMillis());
    }
    static void cancelTimer(Context c) { manager(c).cancel(pending(c,"timer",0)); state(c).edit().putBoolean("timer",false).commit(); }
    static void restore(Context c) {
        JSONArray all=alarms(c);
        for(int i=0;i<all.length();i++) if(all.optJSONObject(i).optBoolean("enabled")) arm(c,all.optJSONObject(i));
        if(state(c).getBoolean("timer",false)) timer(c,Math.max(100,remaining(c)));
    }
}
