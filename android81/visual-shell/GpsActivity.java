package ro.leo.visual;
import ro.leo.i18n.L;
import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.widget.*;
import java.util.*;
import org.json.*;

/** On-demand GPS dashboard. Coordinates stay local unless the user shares a place. */
public final class GpsActivity extends LeoActivity implements LocationListener {
    private LocationManager manager;
    private TextView details;
    private boolean running;
    private Location fix;
    private int satellites,used;
    private final Handler clock=new Handler();
    private final Runnable refresh=new Runnable(){public void run(){paint();if(running)clock.postDelayed(this,1000);}};
    private final GnssStatus.Callback sky=new GnssStatus.Callback(){public void onSatelliteStatusChanged(GnssStatus s){satellites=s.getSatelliteCount();used=0;for(int i=0;i<satellites;i++)if(s.usedInFix(i))used++;paint();}};
    protected void onCreate(Bundle b){super.onCreate(b);manager=(LocationManager)getSystemService(LOCATION_SERVICE);show();}
    private void show(){begin(L.t("GPS Leo"));ScrollView scroll=new ScrollView(this);details=text("",17);scroll.addView(details);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));scroll.setFocusable(true);scroll.requestFocus();left=new Runnable(){public void run(){menu();}};footer(L.t("Opțiuni"),"GPS",L.t("Înapoi"));paint();}
    private void paint(){if(details==null)return;StringBuilder s=new StringBuilder();
        boolean present=manager!=null&&manager.getAllProviders().contains(LocationManager.GPS_PROVIDER);
        s.append(!present?L.t("Serviciul GPS nu este disponibil"):running?L.t("GPS activ · caută sateliți"):L.t("GPS oprit · pornește din Opțiuni"));
        s.append("\n\n").append(L.t("Sateliți · ")).append(used).append('/').append(satellites);
        if(fix!=null){long age=ageSeconds(fix,SystemClock.elapsedRealtimeNanos());s.append("\n\n").append(String.format(Locale.US,"%.6f, %.6f",fix.getLatitude(),fix.getLongitude()));s.append("\n").append(L.t("Precizie · ")).append(fix.hasAccuracy()?Math.round(fix.getAccuracy())+" m":"—");s.append("\n").append(L.t("Viteză · ")).append(fix.hasSpeed()?String.format(Locale.US,"%.1f km/h",fix.getSpeed()*3.6):"—");s.append("\n").append(L.t("Altitudine · ")).append(fix.hasAltitude()?Math.round(fix.getAltitude())+" m":"—");s.append("\n").append(L.t("Vârsta poziției · ")).append(age).append(" s");if(age>120||!running)s.append("\n").append(L.t("Ultima poziție · nu este o măsurare curentă"));}
        else s.append("\n\n").append(L.t("Fără poziție. Prima fixare poate dura; încearcă lângă o fereastră sau afară."));
        details.setText(s.toString());
    }
    static long ageSeconds(Location location,long elapsedNanos){return Math.max(0,(elapsedNanos-location.getElapsedRealtimeNanos())/1000000000L);}
    private void menu(){options(L.t("GPS"),new String[]{running?L.t("Oprește GPS"):L.t("Pornește GPS"),L.t("Salvează locul"),L.t("Locuri favorite"),L.t("Setări localizare")},new Choice(){public void pick(int n){if(n==0){if(running)stop();else start();}else if(n==1)save();else if(n==2)places();else startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));}});}
    private void start(){
        if(manager==null||!manager.getAllProviders().contains(LocationManager.GPS_PROVIDER)){notice(L.t("Serviciul GPS nu este disponibil"));return;}
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},95);return;}
        if(!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)){options(L.t("Activează localizarea GPS?"),new String[]{L.t("Anulează"),L.t("Activează GPS")},new Choice(){public void pick(int n){if(n==1)try{Settings.Secure.putInt(getContentResolver(),Settings.Secure.LOCATION_MODE,Settings.Secure.LOCATION_MODE_HIGH_ACCURACY);start();}catch(SecurityException e){startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));}}});return;}
        try{manager.registerGnssStatusCallback(sky,clock);manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,this);running=true;clock.removeCallbacks(refresh);clock.post(refresh);}catch(RuntimeException e){stop();notice(L.t("GPS nu a putut porni"));}
    }
    private void stop(){running=false;clock.removeCallbacks(refresh);if(manager!=null)try{manager.removeUpdates(this);manager.unregisterGnssStatusCallback(sky);}catch(RuntimeException ignored){}paint();}
    public void onLocationChanged(Location location){if(location!=null&&LocationManager.GPS_PROVIDER.equals(location.getProvider())){fix=new Location(location);paint();}}
    public void onStatusChanged(String p,int s,Bundle b){}
    public void onProviderEnabled(String p){paint();}
    public void onProviderDisabled(String p){stop();}
    private JSONArray saved(){try{return new JSONArray(getSharedPreferences("gps-places",0).getString("places","[]"));}catch(JSONException e){return new JSONArray();}}
    private void store(JSONArray places){getSharedPreferences("gps-places",0).edit().putString("places",places.toString()).apply();}
    private void save(){if(fix==null||!running||ageSeconds(fix,SystemClock.elapsedRealtimeNanos())>120){notice(L.t("Așteaptă o poziție GPS curentă"));return;}final Location selected=new Location(fix);if(saved().length()>=30){notice(L.t("Maximum 30 de locuri"));return;}LinearLayout f=fields(L.t("Salvează locul"));final EditText name=field(f,L.t("Nume"),"",InputType.TYPE_CLASS_TEXT);left=new Runnable(){public void run(){String label=name.getText().toString().trim();if(label.isEmpty()||label.length()>80){notice(L.t("Nume de 1–80 caractere"));return;}try{JSONArray places=saved();JSONObject place=new JSONObject();place.put("name",label);place.put("lat",selected.getLatitude());place.put("lon",selected.getLongitude());places.put(place);store(places);show();}catch(JSONException e){notice(L.t("Locul nu poate fi salvat"));}}};back=new Runnable(){public void run(){show();}};footer(L.t("Salvează"),"",L.t("Șterge"));name.requestFocus();}
    private void places(){final JSONArray places=saved();ArrayList<String> names=new ArrayList<String>();for(int i=0;i<places.length();i++)names.add(places.optJSONObject(i).optString("name"));list(L.t("Locuri favorite"),names,0,new Choice(){public void pick(final int i){final JSONObject p=places.optJSONObject(i);options(p.optString("name"),new String[]{L.t("Coordonate"),L.t("Distribuie"),L.t("Șterge")},new Choice(){public void pick(int n){String coordinates=String.format(Locale.US,"%.6f, %.6f",p.optDouble("lat"),p.optDouble("lon"));if(n==0)notice(coordinates);else if(n==1)startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,p.optString("name")+"\n"+coordinates),L.t("Distribuie")));else options(L.t("Ștergi locul?"),new String[]{L.t("Păstrează"),L.t("Șterge")},new Choice(){public void pick(int yes){if(yes==1){places.remove(i);store(places);places();}}});}});}},"",null);back=new Runnable(){public void run(){show();}};}
    public void onRequestPermissionsResult(int r,String[] p,int[] grant){super.onRequestPermissionsResult(r,p,grant);if(r==95&&grant.length>0&&grant[0]==PackageManager.PERMISSION_GRANTED)start();}
    protected void onPause(){stop();super.onPause();}
    protected void onDestroy(){clock.removeCallbacksAndMessages(null);super.onDestroy();}
}
