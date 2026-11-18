package ro.leo.visual;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import java.io.IOException;
import java.util.Arrays;
import ro.leo.i18n.L;

/** Native keypad entry to the separately installed web browser and text reader. */
public final class BrowserHubActivity extends LeoActivity {
    static final String OPERA="com.opera.mini.native";
    private String address="";
    private int selected;
    private EditText editor;
    private int engine;private Spinner provider;

    protected void onCreate(Bundle state){
        super.onCreate(state);
        if(state!=null){address=state.getString("address","");selected=state.getInt("selected",0);engine=state.getInt("engine",0);}
        if(state==null&&getIntent().getBooleanExtra("edit_address",false))address=getIntent().getStringExtra("address")==null?"":getIntent().getStringExtra("address");
        if(state!=null&&state.getBoolean("editing",false)||state==null&&getIntent().getBooleanExtra("edit_address",false))address();else home();
    }
    protected void onSaveInstanceState(Bundle state){state.putString("address",editor==null?address:editor.getText().toString());state.putInt("selected",selected);state.putBoolean("editing",form);state.putInt("engine",provider==null?engine:provider.getSelectedItemPosition());super.onSaveInstanceState(state);}
    boolean operaAvailable(){return getPackageManager().getLaunchIntentForPackage(OPERA)!=null;}
    private void home(){
        editor=null;provider=null;
        list(L.t("Browser"),Arrays.asList(L.t("Browser web\nAdresă / căutare"),L.t("Mod text\nCitire simplă din taste"),
            "Opera Mini\n"+L.t(operaAvailable()?"Browser extern":"Necesită instalare"),L.t("Ajutor")),selected,
            new Choice(){public void pick(int index){selected=index;if(index==0){engine=0;address();}else if(index==1)openText(null);else if(index==2)openOpera(null);else help();}},
            L.t("Adresă"),new Runnable(){public void run(){address();}});
    }
    private void address(){
        LinearLayout fields=fields(L.t("Adresă / căutare"));
        final EditText input=field(fields,L.t("URL sau cuvinte"),address,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        editor=input;
        provider=new KeypadSpinner(this);provider.setPrompt(L.t("Deschide cu"));
        String[] choices=operaAvailable()?new String[]{L.t("Web integrat"),L.t("Mod text"),"Opera Mini"}:new String[]{L.t("Web integrat"),L.t("Mod text")};
        provider.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,choices));provider.setSelection(Math.min(engine,choices.length-1));
        fields.addView(text(L.t("Deschide cu"),15));fields.addView(provider);
        left=new Runnable(){public void run(){try{address=input.getText().toString();String url=BrowserRules.address(address);engine=provider.getSelectedItemPosition();if(engine==2)openOpera(url);else if(engine==1)openText(url);else openWeb(url);}catch(IOException invalid){notice(L.t("Adresă invalidă"));}}};
        back=new Runnable(){public void run(){address=input.getText().toString();engine=provider.getSelectedItemPosition();home();}};
        footer(L.t("Deschide"),"Editor",L.t("Șterge"));input.requestFocus();
    }
    Intent operaIntent(String url)throws IOException{
        if(url==null)return getPackageManager().getLaunchIntentForPackage(OPERA);
        BrowserRules.checked(url);
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(url)).setPackage(OPERA)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        android.content.pm.ResolveInfo handler=getPackageManager().resolveActivity(intent,android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        if(handler==null)return null;
        return intent.setClassName(handler.activityInfo.packageName,handler.activityInfo.name);
    }
    boolean openOpera(String url){
        Intent intent;
        try{intent=operaIntent(url);}catch(IOException invalid){notice(L.t("Adresă invalidă"));return false;}
        if(intent==null){notice(L.t("Opera Mini nu este instalat"));return false;}
        try{startActivity(intent);return true;}
        catch(ActivityNotFoundException|SecurityException unavailable){notice(L.t("Opera Mini nu poate fi deschis"));return false;}
    }
    private void openWeb(String url){startActivity(new Intent(this,WebBrowserActivity.class).setData(Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
    private void openText(String url){Intent intent=new Intent(this,BrowserActivity.class);if(url!=null)intent.setData(Uri.parse(url));startActivity(intent);}
    private void help(){
        list(L.t("Ajutor Browser"),Arrays.asList(L.t("Browser web\nPagini cu imagini în interfața KeyShell."),L.t("Opera Mini\nBrowser separat. Compatibilitatea depinde de versiunea instalată."),
            L.t("Adresă / căutare\nDeschide un site sau caută cuvintele introduse."),
            L.t("Cursor în pagină\nSăgețile mișcă, OK apasă. La margini derulează. # comută cursorul; în editor folosește Opțiuni → Cursor."),
            L.t("Mod text\nLinkuri și text mai mare, fără JavaScript.")),0,new Choice(){public void pick(int index){}},"",null);
        back=new Runnable(){public void run(){home();}};
    }
}
