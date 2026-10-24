package ro.leo.visual;
import ro.leo.i18n.L;
import ro.leo.i18n.Language;
import android.content.Intent;
import android.os.Bundle;
import java.util.Arrays;

public final class LanguageSettingsActivity extends LeoActivity {
    protected void onResume(){super.onResume();show();}
    private void show(){list("Language / Limbă",Arrays.asList("Română","English"),L.english()?1:0,new Choice(){public void pick(int i){
        try{if(((android.telecom.TelecomManager)getSystemService(TELECOM_SERVICE)).isInCall()){notice(L.t("Încheie mai întâi apelul"));return;}}catch(RuntimeException e){notice(L.t("Starea apelului nu poate fi verificată"));return;}
        if(!Language.select(LanguageSettingsActivity.this,i==1?"en":"ro")){notice(L.t("Preferința nu poate fi schimbată"));return;}
        startActivity(new Intent(LanguageSettingsActivity.this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();
    }},"",null);}
}
