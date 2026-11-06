package ro.leo.visual;
import ro.leo.i18n.L;import android.content.*;
/** Presentation only. Full settings and all installed apps remain reachable. */
final class ShellMode {
 static boolean simple(Context c){return c.getSharedPreferences("appearance",0).getBoolean("simple-mode",false);}
 static boolean appVisible(int id,boolean simple){return !simple||id==0||id==1||id==2||id==3||id==6||id==8;}
 static void choose(final LeoActivity a,final Runnable done){a.options(L.t("Modul interfeței"),new String[]{L.t("Simplu · șase aplicații și setări uzuale"),L.t("Complet · toate categoriile")},new LeoActivity.Choice(){public void pick(int at){a.getSharedPreferences("appearance",0).edit().putBoolean("simple-mode",at==0).apply();done.run();}});}
 static void menuHold(final LeoActivity a,final Runnable done){a.options(L.t("Ține apăsat Meniu"),new String[]{L.t("Aplicații recente"),L.t("Comenzile paginii")},new LeoActivity.Choice(){public void pick(int at){a.getSharedPreferences("appearance",0).edit().putBoolean("menu-hold-help",at==1).apply();done.run();}});}
}
