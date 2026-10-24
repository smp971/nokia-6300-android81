package ro.leo.visual;
import ro.leo.i18n.L;
import android.app.DownloadManager;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.widget.*;
import java.util.*;

public final class DownloadsActivity extends LeoActivity {
    private DownloadManager manager;
    private boolean wifiOnly=true;
    private static final class Entry { long id;int state;String title;long bytes,total; }
    protected void onCreate(Bundle state) { super.onCreate(state);manager=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);wifiOnly=getSharedPreferences("downloads",0).getBoolean("wifi",true); }
    protected void onResume() { super.onResume();wifiOnly=getSharedPreferences("downloads",0).getBoolean("wifi",true); if(manager!=null) load(); }
    private void load() {
        worker.execute(new Runnable() { public void run() {
            final ArrayList<Entry> entries=new ArrayList<Entry>(); final ArrayList<String> labels=new ArrayList<String>();
            try(Cursor c=manager.query(new DownloadManager.Query())) {
                while(c!=null&&c.moveToNext()&&entries.size()<300) { Entry e=new Entry();e.id=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));e.title=c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE));e.state=c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));e.bytes=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));e.total=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));entries.add(e);
                    labels.add(e.title+"\n"+status(e.state)+" · "+(e.total>0?Math.min(100,e.bytes*100/e.total)+"%":e.bytes+" B")); }
                ui(new Runnable() { public void run() { list(L.t("Descărcări Leo"),labels,0,new Choice() { public void pick(int i) { detail(entries.get(i)); } },L.t("Opțiuni"),new Runnable() { public void run() { menu(); } }); } });
            } catch(RuntimeException failure) { ui(new Runnable() { public void run() { notice(L.t("Serviciul de descărcări nu este disponibil"));list(L.t("Descărcări"),Collections.<String>emptyList(),0,new Choice(){public void pick(int i){}},L.t("Nou"),new Runnable(){public void run(){create();}}); } }); }
        }});
    }
    private String status(int state) { return state==DownloadManager.STATUS_SUCCESSFUL?L.t("Complet"):state==DownloadManager.STATUS_RUNNING?L.t("În curs"):state==DownloadManager.STATUS_FAILED?L.t("Eșuat"):state==DownloadManager.STATUS_PAUSED?L.t("În așteptarea rețelei"):L.t("Programat"); }
    private void menu() { options(L.t("Descărcări"),new String[]{L.t("Descărcare nouă"),L.t("Reîmprospătează"),wifiOnly?L.t("Rețea: numai Wi-Fi"):L.t("Rețea: Wi-Fi și mobil")},new Choice() { public void pick(int i) { if(i==0) create();else if(i==1) load();else {wifiOnly=!wifiOnly;getSharedPreferences("downloads",0).edit().putBoolean("wifi",wifiOnly).apply();notice(wifiOnly?L.t("Descărcările noi: numai Wi-Fi"):L.t("Descărcările noi pot folosi date mobile"));} } }); }
    private void create() {
        LinearLayout fields=fields(L.t("Descărcare nouă"));final EditText input=field(fields,L.t("Adresă HTTP / HTTPS"),"https://",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        fields.addView(text(wifiOnly?L.t("Numai Wi-Fi"):L.t("Poate folosi date mobile"),15));
        left=new Runnable() { public void run() {
            final Uri uri=Uri.parse(input.getText().toString().trim());String scheme=uri.getScheme();
            if(!("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme))||uri.getHost()==null||uri.getUserInfo()!=null) {notice(L.t("Adresă HTTP/HTTPS invalidă"));return;}
            options(L.t("Descarcă de la ")+uri.getHost()+"?",new String[]{L.t("Anulează"),L.t("Descarcă")},new Choice() { public void pick(int yes) { if(yes==1) try {
                String name=uri.getLastPathSegment();if(name==null||name.isEmpty()) name="download.bin";name=name.replaceAll("[^a-zA-Z0-9._-]","_");if(name.length()>80) name=name.substring(name.length()-80);
                DownloadManager.Request r=new DownloadManager.Request(uri).setTitle(name).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"leo-"+System.currentTimeMillis()+"-"+name);
                r.setAllowedNetworkTypes(wifiOnly?DownloadManager.Request.NETWORK_WIFI:DownloadManager.Request.NETWORK_WIFI|DownloadManager.Request.NETWORK_MOBILE);manager.enqueue(r);load();
            } catch(RuntimeException failure) { notice(L.t("Descărcarea nu a putut fi programată")); } } });
        }};back=new Runnable(){public void run(){load();}};footer(L.t("Continuă"),"",L.t("Șterge"));input.requestFocus();
    }
    private void detail(final Entry e) {
        options(e.title,new String[]{L.t("Deschide"),L.t("Actualizează lista"),L.t("Anulează / șterge")},new Choice(){public void pick(int i){
            if(i==1) load();else if(i==0) try {Uri uri=manager.getUriForDownloadedFile(e.id);if(uri==null){notice(L.t("Fișierul nu este complet"));return;}String mime=manager.getMimeTypeForDownloadedFile(e.id);startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime==null?"application/octet-stream":mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(RuntimeException unavailable){notice(L.t("Nicio aplicație pentru acest fișier"));}
            else options(L.t("Ștergi descărcarea și fișierul?"),new String[]{L.t("Păstrează"),L.t("Șterge")},new Choice(){public void pick(int yes){if(yes==1){try{manager.remove(e.id);load();}catch(RuntimeException failure){notice(L.t("Operația a eșuat"));}}}});
        }});
    }
}
