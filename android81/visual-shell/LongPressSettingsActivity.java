package ro.leo.visual;
import ro.leo.i18n.L;
import android.os.Bundle;import java.util.*;
public final class LongPressSettingsActivity extends LeoActivity {
    protected void onCreate(Bundle b){super.onCreate(b);show();}
    private void show(){ArrayList<String> rows=new ArrayList<>();for(int n=2;n<=9;n++){int id=HomeKeyActions.app(this,n);rows.add(n+" · "+(id<0?L.t("Apelare rapidă existentă"):AppCatalog.NAMES[id]));}list(L.t("Apăsări lungi pe Home"),rows,0,new Choice(){public void pick(final int at){ArrayList<String> labels=new ArrayList<>();labels.add(L.t("Păstrează apelarea rapidă"));labels.addAll(Arrays.asList(AppCatalog.NAMES));options(L.t("Tasta ")+(at+2),labels.toArray(new String[0]),new Choice(){public void pick(int choice){HomeKeyActions.save(LongPressSettingsActivity.this,at+2,choice-1);show();}});}},L.t("Info"),new Runnable(){public void run(){notice(L.t("Doar 2–9 pe Home. Tastele de apel, deblocare, 0 și 1 rămân protejate. Atribuirea deschide aplicația, fără apel automat."));}});}
}
