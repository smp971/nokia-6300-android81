package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.*;import java.util.*;
final class ThemePacks {
 static final String[] NAMES=L.array(new String[]{"Leo Night · Turcoaz","Nordic · Albastru","Solar · Chihlimbar"});static final int[] THEMES={10,11,12};
 static void apply(Context c,int pack){if(pack<0||pack>=NAMES.length)throw new IllegalArgumentException();SharedPreferences.Editor e=c.getSharedPreferences("appearance",0).edit().putInt("theme",THEMES[pack]).putInt("accent",-1).putBoolean("unified-phone",true);for(String app:StyleSettingsActivity.APPS)e.remove("app-theme-"+app);if(!e.commit())throw new IllegalStateException(L.t("Tema nu poate fi salvată"));}
}
