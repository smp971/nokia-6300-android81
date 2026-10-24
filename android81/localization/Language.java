package ro.leo.i18n;

import android.content.Context;
import android.content.res.Configuration;
import android.provider.Settings;
import java.util.Locale;

/** One secure preference is shared by the shell, camera process and keypad. */
public final class Language {
    public static final String KEY="leo_ui_language";
    private Language(){}
    public static void sync(Context c){
        String code=null;try{code=Settings.Secure.getString(c.getContentResolver(),KEY);}catch(RuntimeException ignored){}
        if(!"en".equals(code)&&!"ro".equals(code))code=c.getSharedPreferences("leo-language",0).getString("language","ro");
        L.useEnglish("en".equals(code));
        Locale locale=L.locale();Locale.setDefault(locale);
        Configuration current=c.getResources().getConfiguration();
        if(!locale.getLanguage().equals(current.getLocales().get(0).getLanguage())){
            Configuration next=new Configuration(current);next.setLocale(locale);
            c.getResources().updateConfiguration(next,c.getResources().getDisplayMetrics());
        }
    }
    public static boolean select(Context c,String code){
        if(!"en".equals(code)&&!"ro".equals(code))throw new IllegalArgumentException("Unsupported language");
        try{if(!Settings.Secure.putString(c.getContentResolver(),KEY,code))return false;}catch(RuntimeException e){return false;}
        c.getSharedPreferences("leo-language",0).edit().putString("language",code).commit();sync(c);return true;
    }
}
