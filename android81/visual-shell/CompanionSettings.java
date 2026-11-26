package ro.leo.visual;
import android.content.*;
import java.io.IOException;
import org.json.*;
/** Non-secret WebUI controls, separate from phone appearance and password verifiers. */
final class CompanionSettings {
    static final String PREFS="companion";
    static final String[] THEMES={"Violet","Grafit","Ocean"};
    static boolean enabled(Context c){return c.getSharedPreferences(PREFS,0).getBoolean("enabled",false);}
    static boolean auto(Context c){return c.getSharedPreferences(PREFS,0).getBoolean("auto-start",true);}
    static boolean feature(Context c,String key){return c.getSharedPreferences(PREFS,0).getBoolean("feature-"+key,true);}
    static int theme(Context c){return Math.max(0,Math.min(2,c.getSharedPreferences(PREFS,0).getInt("web-theme",0)));}
    static void enabled(Context c,boolean value){c.getSharedPreferences(PREFS,0).edit().putBoolean("enabled",value).apply();if(value){if(new CompanionAuth(c).configured())c.startForegroundService(new Intent(c,CompanionService.class));}else c.stopService(new Intent(c,CompanionService.class));}
    static JSONObject snapshot(Context c)throws Exception{return new JSONObject().put("theme",theme(c)).put("autoStart",auto(c)).put("appearance",feature(c,"appearance")).put("layout",feature(c,"layout")).put("files",feature(c,"files"));}
    static void apply(Context c,JSONObject values)throws Exception{
        CompanionModel.exact(values,"theme","autoStart","appearance","layout","files");int theme=CompanionModel.integer(values,"theme");
        if(theme<0||theme>=THEMES.length)throw new IllegalArgumentException("Invalid WebUI theme");
        boolean boot=CompanionModel.bool(values,"autoStart"),appearance=CompanionModel.bool(values,"appearance"),layout=CompanionModel.bool(values,"layout"),files=CompanionModel.bool(values,"files");
        if(!c.getSharedPreferences(PREFS,0).edit().putInt("web-theme",theme).putBoolean("auto-start",boot).putBoolean("feature-appearance",appearance).putBoolean("feature-layout",layout).putBoolean("feature-files",files).commit())throw new IOException("WebUI settings not saved");
    }
}
