package ro.leo.visual;

import android.content.*;
import android.os.Bundle;
import ro.leo.i18n.L;

/** Entry to the separately installed, unprivileged Android KaiSIP port. */
public final class KaiSipActivity extends LeoActivity {
    static final String PACKAGE="ro.n0d3.kaisip";
    protected void onCreate(Bundle state){super.onCreate(state);open();}
    private void open(){
        Intent launch=getPackageManager().getLaunchIntentForPackage(PACKAGE);
        if(launch!=null)try{startActivity(launch);finish();return;}catch(ActivityNotFoundException unavailable){}
        list("keySIP",java.util.Arrays.asList(L.t("keySIP nu este instalat"),L.t("Ajutor")),0,new Choice(){public void pick(int at){
            notice(L.t("Instalează portul keySIP pentru Android. Ai nevoie de un cont SIP și de un server WSS cu WebRTC. Conexiunea permanentă este oprită implicit."));
        }},L.t("Reîncearcă"),new Runnable(){public void run(){open();}});
    }
}
