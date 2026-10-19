package ro.leo.visual;

import android.content.*;
import android.os.Bundle;
import android.text.*;
import android.text.style.URLSpan;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.*;

/** Bounded native HTML-to-text reader. No WebView, JavaScript or TLS bypass. */
public final class BrowserActivity extends LeoActivity {
    private String current="leo:home",title="Browser text";
    private Spanned document;
    private volatile int generation;
    private volatile HttpURLConnection connection;
    protected void onCreate(Bundle b){super.onCreate(b);home();}
    protected void onDestroy(){generation++;HttpURLConnection c=connection;if(c!=null)c.disconnect();super.onDestroy();}
    private void home(){generation++;current="leo:home";title="Browser text";document=Html.fromHtml("<h2>Leo · Web în mod text</h2><p>Adrese, căutare, linkuri, favorite și istoric, din taste.</p><p>Pagini simple: text HTML. Fără JavaScript, formulare web, video sau descărcări. Unele site-uri necesită un motor complet.</p><p><a href='https://example.org/'>Deschide example.org</a></p>",Html.FROM_HTML_MODE_LEGACY);display();}
    private void display(){begin(title);ScrollView scroll=new ScrollView(this);TextView body=text(document.toString(),17);body.setText(document);body.setTextIsSelectable(false);scroll.addView(body);scroll.setFocusable(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));left=new Runnable(){public void run(){menu();}};footer("Opțiuni","Derulare","Înapoi");scroll.requestFocus();back=new Runnable(){public void run(){if(current.equals("leo:home"))finish();else home();}};}
    private void menu(){options("Browser text",new String[]{"Adresă / căutare","Linkuri în pagină","Adaugă la favorite","Favorite","Istoric","Reîncarcă","Pagina de start"},new Choice(){public void pick(int i){if(i==0)address();else if(i==1)links();else if(i==2){if(current.startsWith("http")){remember("bookmarks",30);notice("Favorit salvat");}else notice("Deschide întâi o pagină web");}else if(i==3||i==4)stored(i==3?"bookmarks":"history");else if(i==5){if(current.equals("leo:home"))home();else fetch(current);}else home();}});}
    private void address(){LinearLayout p=fields("Adresă / căutare");final EditText value=field(p,"URL sau cuvinte",current.startsWith("http")?current:"",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);left=new Runnable(){public void run(){String s=value.getText().toString().trim();if(s.isEmpty())return;try{if(!s.contains("://")){if(s.contains(" ")||!s.contains("."))s="https://lite.duckduckgo.com/lite/?q="+URLEncoder.encode(s,"UTF-8");else s="https://"+s;}fetch(s);}catch(Exception e){notice("Adresă invalidă");}}};back=new Runnable(){public void run(){display();}};footer("Deschide","","Șterge");value.requestFocus();}
    private static URL checked(String value)throws IOException{URL u=new URL(value);if(!(u.getProtocol().equals("http")||u.getProtocol().equals("https"))||u.getHost().isEmpty()||u.getUserInfo()!=null)throw new IOException("Doar adrese HTTP/HTTPS fără autentificare în URL");return u;}
    private void fetch(final String destination){
        try{checked(destination);}catch(IOException e){notice("Adresă HTTP/HTTPS invalidă");return;}
        final int token=++generation;final HttpURLConnection old=connection;if(old!=null)old.disconnect();
        begin("Browser text");root.addView(text("Se încarcă…\nÎnapoi: anulează",18),new LinearLayout.LayoutParams(-1,0,1));back=new Runnable(){public void run(){generation++;HttpURLConnection c=connection;if(c!=null)c.disconnect();display();}};footer("","","Anulează");
        worker.execute(new Runnable(){public void run(){try{
            URL url=checked(destination);String html=null;
            for(int redirects=0;redirects<=5;redirects++){
                if(Thread.currentThread().isInterrupted()||token!=generation)return;
                HttpURLConnection c=(HttpURLConnection)url.openConnection();connection=c;c.setInstanceFollowRedirects(false);c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setRequestProperty("User-Agent","LeoText/1.0 (Android 8.1; text reader)");c.setRequestProperty("Accept","text/html,text/plain;q=0.9");
                try{
                    int status=c.getResponseCode();if(status>=300&&status<400){String location=c.getHeaderField("Location");if(location==null||redirects==5)throw new IOException("Redirecționare invalidă");URL next=checked(new URL(url,location).toString());if(url.getProtocol().equals("https")&&!next.getProtocol().equals("https"))throw new IOException("Redirecționare HTTPS spre HTTP refuzată");url=next;continue;}
                    if(status<200||status>=300)throw new IOException("HTTP "+status);
                    String type=c.getContentType();if(type==null)type="text/html";if(!(type.toLowerCase(Locale.ROOT).startsWith("text/html")||type.toLowerCase(Locale.ROOT).startsWith("text/plain")))throw new IOException("Format nesuportat; folosește o pagină text");
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(token!=generation||Thread.currentThread().isInterrupted())return;if(bytes.size()+n>1048576)throw new IOException("Pagina depășește 1 MiB");bytes.write(buf,0,n);}}
                    String encoding="UTF-8";Matcher m=Pattern.compile("charset\\s*=\\s*[\"']?([^;\\s\"']+)",Pattern.CASE_INSENSITIVE).matcher(type);if(m.find()&&Charset.isSupported(m.group(1)))encoding=m.group(1);
                    html=new String(bytes.toByteArray(),encoding);if(type.toLowerCase(Locale.ROOT).startsWith("text/plain"))html="<pre>"+TextUtils.htmlEncode(html)+"</pre>";break;
                }finally{c.disconnect();if(connection==c)connection=null;}
            }
            if(html==null)throw new IOException("Pagină goală");
            String clean=html.replaceAll("(?is)<(script|style|noscript)\\b[^>]*>.*?</\\1\\s*>","");
            final Spanned parsed=Html.fromHtml(clean,Html.FROM_HTML_MODE_LEGACY);final String loaded=url.toString();
            ui(new Runnable(){public void run(){if(token!=generation)return;current=loaded;title=URI.create(loaded).getHost();document=parsed;remember("history",50);display();}});
        }catch(final Exception e){ui(new Runnable(){public void run(){if(token!=generation)return;display();notice("Pagina nu poate fi citită: "+(e instanceof javax.net.ssl.SSLException?"TLS / certificat incompatibil":e.getMessage()));}});}}});
    }
    private void links(){final ArrayList<String> urls=new ArrayList<String>(),labels=new ArrayList<String>();for(URLSpan span:document.getSpans(0,document.length(),URLSpan.class)){if(urls.size()>=150)break;try{String u=new URL(checked(current),span.getURL()).toString();checked(u);urls.add(u);labels.add(document.subSequence(document.getSpanStart(span),document.getSpanEnd(span)).toString()+"\n"+u);}catch(Exception e){try{String u=checked(span.getURL()).toString();urls.add(u);labels.add(u);}catch(Exception ignored){}}}
        list("Linkuri · "+urls.size(),labels,0,new Choice(){public void pick(int i){fetch(urls.get(i));}},"",null);back=new Runnable(){public void run(){display();}};
    }
    private JSONArray records(String key){try{return new JSONArray(getSharedPreferences("browser",0).getString(key,"[]"));}catch(JSONException e){return new JSONArray();}}
    private void remember(String key,int limit){JSONArray old=records(key),next=new JSONArray();try{next.put(new JSONObject().put("url",current).put("title",title));for(int i=0;i<old.length()&&next.length()<limit;i++){JSONObject item=old.getJSONObject(i);if(!current.equals(item.optString("url")))next.put(item);}getSharedPreferences("browser",0).edit().putString(key,next.toString()).apply();}catch(JSONException ignored){}}
    private void stored(final String key){final JSONArray saved=records(key);ArrayList<String> labels=new ArrayList<String>();for(int i=0;i<saved.length();i++){JSONObject o=saved.optJSONObject(i);labels.add(o==null?"Intrare invalidă":o.optString("title")+"\n"+o.optString("url"));}list(key.equals("history")?"Istoric":"Favorite web",labels,0,new Choice(){public void pick(int i){JSONObject o=saved.optJSONObject(i);if(o!=null)fetch(o.optString("url"));}},"Șterge lista",new Runnable(){public void run(){options("Ștergi lista?",new String[]{"Păstrează","Șterge"},new Choice(){public void pick(int i){if(i==1){getSharedPreferences("browser",0).edit().remove(key).apply();stored(key);}}});}});back=new Runnable(){public void run(){display();}};}
}
