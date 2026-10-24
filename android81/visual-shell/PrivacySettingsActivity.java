package ro.leo.visual;
import ro.leo.i18n.L;

import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import java.util.*;

/** Location switch supports scanning; it does not claim a working GNSS port. */
public final class PrivacySettingsActivity extends LeoActivity {
    protected void onResume(){super.onResume();show();}
    private void show() {
        int mode=Settings.Secure.getInt(getContentResolver(),Settings.Secure.LOCATION_MODE,0);
        LocationManager location=(LocationManager)getSystemService(LOCATION_SERVICE);
        List<String> providers=location==null?Collections.<String>emptyList():location.getProviders(true);
        list(L.t("Localizare Leo"),Arrays.asList(L.t("Localizare · ")+(mode==0?L.t("Oprită"):L.t("Pornită")),L.t("Scanare Wi-Fi / Bluetooth"),L.t("Furnizori · ")+(providers.isEmpty()?L.t("Niciunul"):android.text.TextUtils.join(", ",providers)),L.t("GPS · neintegrat")),0,new Choice(){public void pick(int i){
            if(i==0) {
                final boolean enable=Settings.Secure.getInt(getContentResolver(),Settings.Secure.LOCATION_MODE,0)==0;
                options(enable?L.t("Pornește localizarea?"):L.t("Oprește localizarea?"),new String[]{L.t("Anulează"),enable?L.t("Pornește pentru scanare"):L.t("Oprește")},new Choice(){public void pick(int yes){
                    if(yes!=1)return;
                    try {Settings.Secure.putInt(getContentResolver(),Settings.Secure.LOCATION_MODE,enable?Settings.Secure.LOCATION_MODE_BATTERY_SAVING:Settings.Secure.LOCATION_MODE_OFF);show();}catch(SecurityException denied){notice(L.t("Acces la setările sistemului indisponibil"));}
                }});
            }else if(i==1) notice(L.t("Android 8 cere localizare activă și permisiune pentru scanare. Adăugarea Wi-Fi manuală funcționează fără scanare."));
            else notice(L.t("Comutatorul nu confirmă funcționarea GPS sau obținerea unei poziții."));
        }},L.t("Actualiz."),new Runnable(){public void run(){show();}});
    }
}
