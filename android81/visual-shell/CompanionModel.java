package ro.leo.visual;
import android.app.ActivityManager;
import android.content.*;
import android.os.*;
import java.io.IOException;
import java.util.*;
import org.json.*;
/** Explicit appearance/folder API; never exposes shell commands, contacts, SMS or app launching. */
final class CompanionModel {
    static JSONObject snapshot(Context c)throws Exception{
        JSONObject out=new JSONObject();JSONArray apps=new JSONArray(),folders=new JSONArray(),themes=new JSONArray();
        for(int id:AppCatalog.order(c))if(!AppCatalog.settingsOnly(id))apps.put(new JSONObject().put("id",id).put("name",AppCatalog.NAMES[id]).put("folder",LauncherFolders.folder(c,id)));
        for(int i=0;i<LauncherFolders.COUNT;i++)folders.put(new JSONObject().put("id",i).put("name",LauncherFolders.label(LauncherFolders.FIRST+i)));
        for(int i=0;i<Appearance.THEMES.length;i++)themes.put(new JSONObject().put("id",i).put("name",Appearance.THEMES[i]).put("color",String.format(Locale.ROOT,"#%06x",Appearance.COLORS[i][2]&0xffffff)).put("background",String.format(Locale.ROOT,"#%06x",new Appearance.Palette(Appearance.COLORS[i]).background&0xffffff)));
        SharedPreferences p=c.getSharedPreferences("appearance",0);
        JSONObject settings=new JSONObject().put("theme",p.getInt("theme",0)).put("list",p.getBoolean("list",false)).put("foldersEnabled",c.getSharedPreferences(LauncherFolders.PREFERENCES,0).getBoolean("enabled",true)).put("simple",ShellMode.simple(c));
        Intent battery=c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level=battery==null?-1:battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=battery==null?100:battery.getIntExtra(BatteryManager.EXTRA_SCALE,100);
        ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();((ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memory);
        JSONObject status=new JSONObject().put("platform","KeyShell · Android 8.1").put("api",Build.VERSION.SDK_INT)
            .put("battery",level<0?-1:100*level/Math.max(1,scale)).put("charging",battery!=null&&battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0)
            .put("health",ShellHealth.status(c)).put("free_bytes",CompanionServer.folder(c).getUsableSpace()).put("ram_free",memory.availMem).put("ram_total",memory.totalMem)
            .put("uptime_ms",SystemClock.elapsedRealtime()).put("scope","KeyShell appearance, launcher folders and Companion files");
        return out.put("status",status).put("apps",apps).put("folders",folders).put("themes",themes).put("settings",settings);
    }
    static void exact(JSONObject object,String... keys)throws Exception{if(object.length()!=keys.length)throw new IllegalArgumentException("Invalid settings");for(String key:keys)if(!object.has(key))throw new IllegalArgumentException("Missing setting");}
    static int integer(JSONObject object,String key)throws Exception{Object value=object.get(key);if(!(value instanceof Integer))throw new IllegalArgumentException("Integer required");return (Integer)value;}
    static boolean bool(JSONObject object,String key)throws Exception{Object value=object.get(key);if(!(value instanceof Boolean))throw new IllegalArgumentException("Boolean required");return (Boolean)value;}
    static synchronized void appearance(Context c,JSONObject values)throws Exception{
        exact(values,"theme","list");int theme=integer(values,"theme");boolean list=bool(values,"list");if(theme<0||theme>=Appearance.THEMES.length)throw new IllegalArgumentException("Invalid theme");
        if(!c.getSharedPreferences("appearance",0).edit().putInt("theme",theme).putBoolean("list",list).commit())throw new IOException("Settings not saved");
    }
    static synchronized void layout(Context c,JSONObject values)throws Exception{
        exact(values,"enabled","moves");boolean enabled=bool(values,"enabled");JSONArray moves=values.getJSONArray("moves");if(moves.length()>AppCatalog.NAMES.length)throw new IllegalArgumentException("Too many moves");
        Set<Integer> ids=new HashSet<Integer>();int[] apps=new int[moves.length()],parents=new int[moves.length()];
        for(int i=0;i<moves.length();i++){JSONObject move=moves.getJSONObject(i);exact(move,"id","folder");int app=integer(move,"id"),folder=integer(move,"folder");
            if(app<0||app>=AppCatalog.NAMES.length||AppCatalog.settingsOnly(app)||!ids.add(app)||folder< -1||folder>=LauncherFolders.COUNT)throw new IllegalArgumentException("Invalid move");apps[i]=app;parents[i]=folder;}
        SharedPreferences.Editor edit=c.getSharedPreferences(LauncherFolders.PREFERENCES,0).edit().putBoolean("enabled",enabled);
        for(int i=0;i<apps.length;i++){String key="app"+apps[i];if(parents[i]==LauncherFolders.defaultFolder(apps[i]))edit.remove(key);else edit.putInt(key,parents[i]);}
        if(!edit.commit())throw new IOException("Layout not saved");
    }
}
