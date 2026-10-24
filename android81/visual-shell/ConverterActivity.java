package ro.leo.visual;
import ro.leo.i18n.L;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import java.util.*;
import java.text.DecimalFormat;

public final class ConverterActivity extends LeoActivity {
    private int category,from,to=1;
    protected void onCreate(Bundle state) { super.onCreate(state); categories(); }
    private void categories() {
        list(L.t("Convertor"),Arrays.asList(UnitConversion.CATEGORIES),category,new Choice() { public void pick(int i) { category=i; from=0; to=1; units(false); } },"",null);
    }
    private void units(final boolean target) {
        list(target?L.t("Unitate rezultat"):L.t("Unitate sursă"),Arrays.asList(UnitConversion.UNITS[category]),target?to:from,new Choice() { public void pick(int i) {
            if(target) { to=i; editor(""); } else { from=i; units(true); }
        } },"",null);
        back=new Runnable() { public void run() { if(target) units(false); else categories(); } };
    }
    private void editor(String initial) {
        LinearLayout fields=fields(UnitConversion.CATEGORIES[category]);
        final EditText value=field(fields,UnitConversion.UNITS[category][from]+" → "+UnitConversion.UNITS[category][to],initial,InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        final TextView answer=text(L.t("Introdu valoarea și apasă Calculează"),20); fields.addView(answer);
        left=new Runnable() { public void run() {
            try { double result=UnitConversion.convert(category,from,to,Double.parseDouble(value.getText().toString().trim().replace(',','.')));
                answer.setText(new DecimalFormat("0.##########").format(result)+" "+UnitConversion.UNITS[category][to]);
            } catch(RuntimeException invalid) { answer.setText(L.t("Valoare invalidă sau sub zero absolut")); }
        }};
        back=new Runnable() { public void run() { units(true); } }; footer(L.t("Calculează"),"",L.t("Șterge")); value.requestFocus();
    }
}
