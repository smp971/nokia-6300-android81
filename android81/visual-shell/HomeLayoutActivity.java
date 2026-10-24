package ro.leo.visual;
import ro.leo.i18n.L;
import android.os.Bundle;import java.util.*;
public final class HomeLayoutActivity extends LeoActivity {
 static final String[] LABELS=L.array(new String[]{"Meteo","Următoarea alarmă","Muzică / FM","Contacte favorite"});
 protected void onCreate(Bundle b){super.onCreate(b);show(0);}
 private void show(int selected){final int[] ids=HomeCards.order(getSharedPreferences("appearance",0).getString("home-card-order",""));ArrayList<String> rows=new ArrayList<String>();for(int id:ids)rows.add(LABELS[id]+" · "+(getSharedPreferences("appearance",0).getBoolean(id==3?"home-favorites":HomeCards.KEYS[id],id!=3)?L.t("Da"):L.t("Nu")));list(L.t("Cardurile Home"),rows,selected,new Choice(){public void pick(final int at){options(LABELS[ids[at]],new String[]{L.t("Mută mai sus"),L.t("Mută mai jos"),L.t("Arată / ascunde")},new Choice(){public void pick(int action){if(action==2){String key=ids[at]==3?"home-favorites":HomeCards.KEYS[ids[at]];getSharedPreferences("appearance",0).edit().putBoolean(key,!getSharedPreferences("appearance",0).getBoolean(key,ids[at]!=3)).apply();show(at);}else{int target=CircularNavigation.step(at,action==0?-1:1,ids.length),id=ids[at];ids[at]=ids[target];ids[target]=id;ArrayList<Integer> order=new ArrayList<Integer>();for(int n:ids)order.add(n);getSharedPreferences("appearance",0).edit().putString("home-card-order",android.text.TextUtils.join(",",order)).apply();show(target);}}});}},"",null);}
}
