package ro.leo.visual;

import ro.leo.i18n.L;
import android.app.*;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.os.*;
import android.provider.Settings;
import java.util.*;

/** Bounded local readiness checks; never starts calls, records audio or changes roles. */
public final class DailyReadinessActivity extends LeoActivity {
    protected void onCreate(Bundle state){super.onCreate(state);show();}
    private String state(boolean ready){return ready?L.t("Pregătit"):L.t("De verificat");}
    private void show(){
        final ArrayList<String> rows=new ArrayList<>();
        ResolveInfo home=getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME),android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        rows.add(L.t("Home Leo")+"\n"+state(home!=null&&home.activityInfo!=null&&getPackageName().equals(home.activityInfo.packageName)));
        String ime=Settings.Secure.getString(getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD);
        rows.add(L.t("Tastatură T9")+"\n"+state(ime!=null&&ime.startsWith("ro.leo.keypad/")));
        rows.add(L.t("Meniu global și mouse")+"\n"+state(LeoPointerService.connected()));
        String listeners=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");
        rows.add(L.t("Acces notificări Leo")+"\n"+state(listeners!=null&&listeners.contains(getPackageName()+"/")));
        ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();
        ((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(memory);
        rows.add(L.t("Memorie disponibilă")+"\n"+(memory.availMem/1048576)+" MiB · "+state(!memory.lowMemory));
        SharedPreferences boot=getSharedPreferences("boot-readiness",0);
        long first=boot.getLong("first_home_ms",-1),healthy=boot.getLong("healthy_ms",-1);
        rows.add(L.t("Pornirea interfeței")+"\n"+(healthy<0?L.t("În curs de verificare"):
                String.format(java.util.Locale.US,"%.1f s · Home",healthy/1000.0)));
        rows.add(L.t("Diagnostic detaliat")+"\n"+L.t("Date locale, fără teste de apel"));
        list(L.t("Pregătit de utilizare"),rows,0,new Choice(){public void pick(int at){
            if(at==6)startActivity(new Intent(DailyReadinessActivity.this,DiagnosticActivity.class));
            else notice(at==5?L.t("Milestone software de la pornire; nu măsoară primul pixel de pe ecran."):rows.get(at));
        }},L.t("Reverifică"),new Runnable(){public void run(){show();}});
    }
}
