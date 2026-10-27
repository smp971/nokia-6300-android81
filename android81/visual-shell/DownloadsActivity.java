package ro.leo.visual;
import ro.leo.i18n.L;
import android.app.DownloadManager;
import android.content.*;import android.database.Cursor;import android.net.Uri;import android.os.*;
import android.text.InputType;import android.widget.*;import java.util.*;

/** DownloadManager owns transfers. Poll only the visible list, never the editor/dialog. */
public final class DownloadsActivity extends LeoActivity {
    private DownloadManager manager;private boolean wifiOnly=true,visible,listing,loading,listPage;
    private int generation,selected;private final Handler refresh=new Handler();
    private final ArrayList<Entry> entries=new ArrayList<>();
    static final class Entry {long id,bytes,total;int state,reason;String title,url;}
    private final Runnable poll=new Runnable(){public void run(){if(visible&&listing)load();}};
    protected void onCreate(Bundle state){super.onCreate(state);manager=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);}
    protected void onResume(){super.onResume();visible=true;wifiOnly=getSharedPreferences("downloads",0).getBoolean("wifi",true);listing=true;load();}
    protected void onPause(){visible=false;generation++;loading=false;refresh.removeCallbacksAndMessages(null);super.onPause();}
    void onListSelection(String title,int index){if(title.equals(L.t("Descărcări Leo")))selected=index;}
    public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused&&visible&&listPage&&!listing)load();}
    private void stopPolling(){listing=false;generation++;loading=false;refresh.removeCallbacksAndMessages(null);}
    private void load(){
        if(!visible||loading||manager==null)return;listPage=true;listing=true;loading=true;final int ticket=++generation;
        worker.execute(new Runnable(){public void run(){final ArrayList<Entry> found=new ArrayList<>();
            try(Cursor c=manager.query(new DownloadManager.Query())){
                while(c!=null&&c.moveToNext()&&found.size()<300){Entry e=new Entry();e.id=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));e.title=c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE));e.url=c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_URI));e.state=c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));e.reason=c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));e.bytes=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));e.total=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));found.add(e);}
                ui(new Runnable(){public void run(){if(ticket!=generation||!visible||!listing)return;loading=false;ArrayList<String> labels=new ArrayList<>();boolean pending=false;boolean same=found.size()==entries.size();for(int i=0;i<found.size();i++){Entry e=found.get(i);labels.add(e.title+"\n"+status(e.state)+" · "+progress(e.bytes,e.total));pending|=e.state==DownloadManager.STATUS_RUNNING||e.state==DownloadManager.STATUS_PENDING||e.state==DownloadManager.STATUS_PAUSED;if(same&&e.id!=entries.get(i).id)same=false;}entries.clear();entries.addAll(found);
                    if(!same||!replaceRows(L.t("Descărcări Leo"),labels))list(L.t("Descărcări Leo"),labels,selected,new Choice(){public void pick(int at){if(at<entries.size())detail(entries.get(at));}},L.t("Opțiuni"),new Runnable(){public void run(){menu();}});
                    refresh.removeCallbacks(poll);if(pending)refresh.postDelayed(poll,2000);
                }});
            }catch(RuntimeException failure){ui(new Runnable(){public void run(){if(ticket!=generation)return;loading=false;notice(L.t("Serviciul de descărcări nu este disponibil"));list(L.t("Descărcări"),Collections.<String>emptyList(),0,new Choice(){public void pick(int i){}},L.t("Nou"),new Runnable(){public void run(){create();}});}});}
        }});
    }
    static String progress(long bytes,long total){return total>0?Math.min(100,Math.max(0,bytes)*100.0/total)==100?"100%":String.format(java.util.Locale.ROOT,"%.0f%%",Math.min(100,Math.max(0,bytes)*100.0/total)):Math.max(0,bytes)+" B";}
    static boolean validUrl(String raw){try{Uri u=Uri.parse(raw);return ("https".equalsIgnoreCase(u.getScheme())||"http".equalsIgnoreCase(u.getScheme()))&&u.getHost()!=null&&u.getUserInfo()==null;}catch(RuntimeException e){return false;}}
    private String status(int state){return state==DownloadManager.STATUS_SUCCESSFUL?L.t("Complet"):state==DownloadManager.STATUS_RUNNING?L.t("În curs"):state==DownloadManager.STATUS_FAILED?L.t("Eșuat"):state==DownloadManager.STATUS_PAUSED?L.t("În așteptarea rețelei"):L.t("Programat");}
    private void menu(){stopPolling();options(L.t("Descărcări"),new String[]{L.t("Descărcare nouă"),L.t("Reîmprospătează"),wifiOnly?L.t("Rețea: numai Wi-Fi"):L.t("Rețea: Wi-Fi și mobil")},new Choice(){public void pick(int i){if(i==0)create();else if(i==1)load();else{wifiOnly=!wifiOnly;getSharedPreferences("downloads",0).edit().putBoolean("wifi",wifiOnly).apply();load();}}});}
    static DownloadManager.Request request(String raw,boolean wifiOnly){Uri uri=Uri.parse(raw);if(!validUrl(raw))throw new IllegalArgumentException();String name=uri.getLastPathSegment();if(name==null||name.isEmpty())name="download.bin";name=name.replaceAll("[^a-zA-Z0-9._-]","_");if(name.length()>80)name=name.substring(name.length()-80);
        DownloadManager.Request r=new DownloadManager.Request(uri).setTitle(name).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"leo-"+System.currentTimeMillis()+"-"+name);r.setAllowedNetworkTypes(wifiOnly?DownloadManager.Request.NETWORK_WIFI:DownloadManager.Request.NETWORK_WIFI|DownloadManager.Request.NETWORK_MOBILE);return r;
    }
    private void enqueue(String raw){manager.enqueue(request(raw,wifiOnly));}
    private void create(){listPage=false;stopPolling();LinearLayout f=fields(L.t("Descărcare nouă"));final EditText input=field(f,L.t("Adresă HTTP / HTTPS"),"https://",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);f.addView(text(wifiOnly?L.t("Numai Wi-Fi"):L.t("Poate folosi date mobile"),15));left=new Runnable(){public void run(){final String raw=input.getText().toString().trim();if(!validUrl(raw)){notice(L.t("Adresă HTTP/HTTPS invalidă"));return;}options(L.t("Descarcă de la ")+Uri.parse(raw).getHost()+"?",new String[]{L.t("Anulează"),L.t("Descarcă")},new Choice(){public void pick(int yes){if(yes==1)try{enqueue(raw);load();}catch(RuntimeException failure){notice(L.t("Descărcarea nu a putut fi programată"));}else create();}});}};back=new Runnable(){public void run(){load();}};footer(L.t("Continuă"),"",L.t("Șterge"));input.requestFocus();}
    private void detail(final Entry e){stopPolling();options(e.title,new String[]{L.t("Deschide"),L.t("Actualizează lista"),L.t("Anulează / șterge"),L.t("Reîncearcă"),L.t("Detalii")},new Choice(){public void pick(int i){
        if(i==1)load();else if(i==0)try{Uri uri=manager.getUriForDownloadedFile(e.id);if(uri==null){notice(L.t("Fișierul nu este complet"));return;}String mime=manager.getMimeTypeForDownloadedFile(e.id);ShareHubActivity.view(DownloadsActivity.this,uri,mime==null?"application/octet-stream":mime);}catch(RuntimeException unavailable){notice(L.t("Nicio aplicație pentru acest fișier"));}
        else if(i==2)options(L.t("Ștergi descărcarea și fișierul?"),new String[]{L.t("Păstrează"),L.t("Șterge")},new Choice(){public void pick(int yes){if(yes==1)try{manager.remove(e.id);}catch(RuntimeException failure){notice(L.t("Operația a eșuat"));}load();}});
        else if(i==3){if(e.state!=DownloadManager.STATUS_FAILED){notice(L.t("Reîncercarea este disponibilă pentru descărcări eșuate"));return;}options(L.t("Reîncearcă descărcarea?"),new String[]{L.t("Anulează"),L.t("Reîncearcă")},new Choice(){public void pick(int yes){if(yes==1)try{enqueue(e.url);notice(L.t("O copie nouă a fost programată; intrarea veche este păstrată."));load();}catch(RuntimeException failed){notice(L.t("Descărcarea nu a putut fi programată"));}}});}
        else notice(status(e.state)+" · "+progress(e.bytes,e.total)+"\n"+e.bytes+" / "+e.total+" B\n"+L.t("Cod serviciu · ")+e.reason);
    }});}
}
