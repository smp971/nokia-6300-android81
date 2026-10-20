package ro.leo.visual;
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
                ui(new Runnable() { public void run() { list("Descărcări Leo",labels,0,new Choice() { public void pick(int i) { detail(entries.get(i)); } },"Opțiuni",new Runnable() { public void run() { menu(); } }); } });
            } catch(RuntimeException failure) { ui(new Runnable() { public void run() { notice("Serviciul de descărcări nu este disponibil");list("Descărcări",Collections.<String>emptyList(),0,new Choice(){public void pick(int i){}},"Nou",new Runnable(){public void run(){create();}}); } }); }
        }});
    }
    private String status(int state) { return state==DownloadManager.STATUS_SUCCESSFUL?"Complet":state==DownloadManager.STATUS_RUNNING?"În curs":state==DownloadManager.STATUS_FAILED?"Eșuat":state==DownloadManager.STATUS_PAUSED?"În așteptarea rețelei":"Programat"; }
    private void menu() { options("Descărcări",new String[]{"Descărcare nouă","Reîmprospătează",wifiOnly?"Rețea: numai Wi-Fi":"Rețea: Wi-Fi și mobil"},new Choice() { public void pick(int i) { if(i==0) create();else if(i==1) load();else {wifiOnly=!wifiOnly;getSharedPreferences("downloads",0).edit().putBoolean("wifi",wifiOnly).apply();notice(wifiOnly?"Descărcările noi: numai Wi-Fi":"Descărcările noi pot folosi date mobile");} } }); }
    private void create() {
        LinearLayout fields=fields("Descărcare nouă");final EditText input=field(fields,"Adresă HTTP / HTTPS","https://",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        fields.addView(text(wifiOnly?"Numai Wi-Fi":"Poate folosi date mobile",15));
        left=new Runnable() { public void run() {
            final Uri uri=Uri.parse(input.getText().toString().trim());String scheme=uri.getScheme();
            if(!("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme))||uri.getHost()==null||uri.getUserInfo()!=null) {notice("Adresă HTTP/HTTPS invalidă");return;}
            options("Descarcă de la "+uri.getHost()+"?",new String[]{"Anulează","Descarcă"},new Choice() { public void pick(int yes) { if(yes==1) try {
                String name=uri.getLastPathSegment();if(name==null||name.isEmpty()) name="download.bin";name=name.replaceAll("[^a-zA-Z0-9._-]","_");if(name.length()>80) name=name.substring(name.length()-80);
                DownloadManager.Request r=new DownloadManager.Request(uri).setTitle(name).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"leo-"+System.currentTimeMillis()+"-"+name);
                r.setAllowedNetworkTypes(wifiOnly?DownloadManager.Request.NETWORK_WIFI:DownloadManager.Request.NETWORK_WIFI|DownloadManager.Request.NETWORK_MOBILE);manager.enqueue(r);load();
            } catch(RuntimeException failure) { notice("Descărcarea nu a putut fi programată"); } } });
        }};back=new Runnable(){public void run(){load();}};footer("Continuă","","Șterge");input.requestFocus();
    }
    private void detail(final Entry e) {
        options(e.title,new String[]{"Deschide","Actualizează lista","Anulează / șterge"},new Choice(){public void pick(int i){
            if(i==1) load();else if(i==0) try {Uri uri=manager.getUriForDownloadedFile(e.id);if(uri==null){notice("Fișierul nu este complet");return;}String mime=manager.getMimeTypeForDownloadedFile(e.id);startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime==null?"application/octet-stream":mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(RuntimeException unavailable){notice("Nicio aplicație pentru acest fișier");}
            else options("Ștergi descărcarea și fișierul?",new String[]{"Păstrează","Șterge"},new Choice(){public void pick(int yes){if(yes==1){try{manager.remove(e.id);load();}catch(RuntimeException failure){notice("Operația a eșuat");}}}});
        }});
    }
}
