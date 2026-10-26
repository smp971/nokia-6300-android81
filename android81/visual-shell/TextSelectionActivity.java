package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.*;import android.os.*;import android.text.InputType;import android.widget.*;
/** Keypad range selection; copying only follows an explicit softkey action. */
public final class TextSelectionActivity extends LeoActivity {
    protected void onCreate(Bundle state){super.onCreate(state);final String body=getIntent().getStringExtra("text");if(body==null||body.length()>32768){finish();return;}
        final int count=body.codePointCount(0,body.length());LinearLayout f=fields(L.t("Selectează text"));
        TextView sample=text(body,15);sample.setMaxLines(4);f.addView(sample);
        final EditText start=field(f,L.t("De la caracterul (1)"),"1",InputType.TYPE_CLASS_NUMBER);
        final EditText end=field(f,L.t("Până la caracterul (inclusiv)"),String.valueOf(count),InputType.TYPE_CLASS_NUMBER);
        left=new Runnable(){public void run(){try{final String selection=TextRange.select(body,Integer.parseInt(start.getText().toString())-1,Integer.parseInt(end.getText().toString()));options(selection.isEmpty()?L.t("Selecție goală"):selection,new String[]{L.t("Revino la selecție"),L.t("Copiază selecția")},new Choice(){public void pick(int n){if(n==1){((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("SMS",selection));notice(L.t("Text copiat"));finish();}}});}catch(RuntimeException e){notice(L.t("Interval invalid"));}}};
        footer(L.t("Previzualizare"),"1–"+count,L.t("Șterge"));start.requestFocus();
    }
}
