package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.*;import android.os.Bundle;import java.util.*;
public final class AccessibilitySettingsActivity extends LeoActivity {
 protected void onCreate(Bundle b){super.onCreate(b);show();}
 private void show(){final SharedPreferences p=getSharedPreferences("accessibility",0);list(L.t("Accesibilitate Leo"),Arrays.asList(L.t("Contrast ridicat")+" · "+(p.getBoolean("contrast",false)?L.t("Pornit"):L.t("Oprit")),L.t("Dimensiunea textului"),L.t("Vibrații la SMS")+" · "+(p.getBoolean("sms",false)?L.t("Pornit"):L.t("Oprit")),L.t("Vibrații la mementouri")+" · "+(p.getBoolean("calendar",false)?L.t("Pornit"):L.t("Oprit")),L.t("Comenzile paginii · Meniu lung")),0,new Choice(){public void pick(int n){if(n==0||n==2||n==3){String key=n==0?"contrast":n==2?"sms":"calendar";p.edit().putBoolean(key,!p.getBoolean(key,false)).apply();show();}else if(n==1)options(L.t("Text în liste și formulare"),new String[]{"Normal",L.t("Mare"),L.t("Foarte mare")},new Choice(){public void pick(int value){p.edit().putInt("text",value).apply();show();}});else ContextHelp.open(AccessibilitySettingsActivity.this,"AccessibilitySettingsActivity");}},"",null);}
}
