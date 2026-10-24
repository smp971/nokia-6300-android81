package ro.leo.visual;
import ro.leo.i18n.L;
import ro.leo.i18n.Language;
import android.app.*;import android.content.*;import android.graphics.Bitmap;import android.os.*;import android.provider.Settings;import android.view.*;import android.widget.*;import java.io.*;

/** Local language round-trip; no calls, messages, media or network changes. */
public final class LocalizationTests extends Instrumentation {
    private int checks;private LeoActivity activity;
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    public void onCreate(Bundle b){start();}
    public void onStart(){Bundle result=new Bundle();final Context c=getTargetContext();String before=Settings.Secure.getString(c.getContentResolver(),Language.KEY);String fallback=c.getSharedPreferences("leo-language",0).getString("language",null);
        final String order=c.getSharedPreferences("appearance",0).getString("app-order","");final int theme=c.getSharedPreferences("appearance",0).getInt("theme",0);final int mode=Settings.Secure.getInt(c.getContentResolver(),"leo_keypad_default_mode",-1);
        try{
            check(Language.select(c,"ro"),"select Romanian");
            check(AppCatalog.NAMES[0].equals("Telefon"),"Romanian app catalog");
            String[] labels=L.array(new String[]{"Muzică","Înapoi"});
            check(Language.select(c,"en"),"select English");
            check("en".equals(Settings.Secure.getString(c.getContentResolver(),Language.KEY)),"shared persistent preference");
            check(AppCatalog.NAMES[0].equals("Phone")&&AppCatalog.NAMES[2].equals("Messages"),"loaded catalog updates live");
            check(labels[0].equals("Music")&&labels[1].equals("Back"),"registered arrays update live");
            check(L.locale().getLanguage().equals("en"),"English date locale");
            check(CallPresentation.state(7).equals("Call ended"),"call status localized without placing a call");
            check(L.t("Răspunde").equals("Answer"),"answer for calls");
            check(L.t("Răspunde","message").equals("Reply"),"reply for messages");
            check(NetworkStartup.label("started",false,false,40000).equals("NETWORK · FINDING OPERATOR"),"boot status localized");
            java.lang.reflect.Field f=SettingsActivity.class.getDeclaredField("ROWS");f.setAccessible(true);String[][] rows=(String[][])f.get(null);
            check(rows[1][0].equals("Display and keypad"),"nested settings categories localized");
            setInTouchMode(false);
            activity=(LeoActivity)startActivitySync(new Intent(c,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            runOnMainSync(new Runnable(){public void run(){check(((TextView)activity.root.getChildAt(0)).getText().toString().startsWith("Leo Settings"),"English header");LinearLayout bar=(LinearLayout)activity.root.getChildAt(activity.root.getChildCount()-1);check(((TextView)bar.getChildAt(2)).getText().toString().equals("Back"),"English softkeys");}});
            Bitmap image=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(c.getFilesDir(),"localization-settings-en.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
            runOnMainSync(new Runnable(){public void run(){LinearLayout fields=activity.fields(L.t("Nume"));String user="Telefon · Ștefan";EditText edit=activity.field(fields,L.t("Nume"),user,android.text.InputType.TYPE_CLASS_TEXT);check(edit.getText().toString().equals(user),"user-authored Romanian content preserved");activity.footer("","","Back");}});
            runOnMainSync(new Runnable(){public void run(){activity.finish();}});activity=null;
            check(Language.select(c,"ro"),"return to Romanian");
            check(AppCatalog.NAMES[0].equals("Telefon")&&labels[0].equals("Muzică"),"loaded arrays restore original Romanian");
            check(rows[1][0].equals("Ecran și taste"),"nested arrays restore original Romanian");
            check(c.getSharedPreferences("appearance",0).getString("app-order","").equals(order)&&c.getSharedPreferences("appearance",0).getInt("theme",0)==theme,"app order and theme unchanged");
            check(Settings.Secure.getInt(c.getContentResolver(),"leo_keypad_default_mode",-1)==mode,"input mode unchanged");
            result.putString("result","PASS "+checks+" localization checks");
        }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));}
        finally{if(activity!=null)runOnMainSync(new Runnable(){public void run(){activity.finish();}});Settings.Secure.putString(c.getContentResolver(),Language.KEY,before);android.content.SharedPreferences.Editor e=c.getSharedPreferences("leo-language",0).edit();if(fallback==null)e.remove("language");else e.putString("language",fallback);e.commit();Language.sync(c);}
        finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}
