package ro.leo.visual;

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
        list("Localizare Leo",Arrays.asList("Localizare · "+(mode==0?"Oprită":"Pornită"),"Scanare Wi-Fi / Bluetooth","Furnizori · "+(providers.isEmpty()?"Niciunul":android.text.TextUtils.join(", ",providers)),"GPS · neintegrat"),0,new Choice(){public void pick(int i){
            if(i==0) {
                final boolean enable=Settings.Secure.getInt(getContentResolver(),Settings.Secure.LOCATION_MODE,0)==0;
                options(enable?"Pornește localizarea?":"Oprește localizarea?",new String[]{"Anulează",enable?"Pornește pentru scanare":"Oprește"},new Choice(){public void pick(int yes){
                    if(yes!=1)return;
                    try {Settings.Secure.putInt(getContentResolver(),Settings.Secure.LOCATION_MODE,enable?Settings.Secure.LOCATION_MODE_BATTERY_SAVING:Settings.Secure.LOCATION_MODE_OFF);show();}catch(SecurityException denied){notice("Acces la setările sistemului indisponibil");}
                }});
            }else if(i==1) notice("Android 8 cere localizare activă și permisiune pentru scanare. Adăugarea Wi-Fi manuală funcționează fără scanare.");
            else notice("Comutatorul nu confirmă funcționarea GPS sau obținerea unei poziții.");
        }},"Actualiz.",new Runnable(){public void run(){show();}});
    }
}
