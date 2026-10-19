package ro.leo.visual;
import android.content.Intent;
import android.os.Bundle;
import java.util.Arrays;

public final class KeypadLockSettingsActivity extends LeoActivity {
    protected void onResume(){super.onResume();show();}
    private void show(){
        list("Blocarea tastelor",Arrays.asList("Blochează acum","La stingerea ecranului · "+(getSharedPreferences("keyguard",0).getBoolean("screen_off",false)?"Da":"Nu"),"Deblocare · soft stânga, apoi *"),0,new Choice(){public void pick(int i){
            if(i==0)startActivity(new Intent(KeypadLockSettingsActivity.this,KeypadLockActivity.class));
            else if(i==1){boolean old=getSharedPreferences("keyguard",0).getBoolean("screen_off",false);getSharedPreferences("keyguard",0).edit().putBoolean("screen_off",!old).apply();show();}
            else notice("Soft stânga, apoi * în 3 secunde. Protecție contra apăsărilor accidentale, fără PIN.");
        }},"",null);
    }
}
