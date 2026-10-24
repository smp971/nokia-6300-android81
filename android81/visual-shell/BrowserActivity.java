package ro.leo.visual;
import ro.leo.i18n.L;

import android.content.*;
import android.os.Bundle;
import android.text.*;
import android.text.style.URLSpan;
import android.widget.*;
import android.view.*;
import android.graphics.Color;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.*;

/** Bounded native HTML-to-text reader. No WebView, JavaScript or TLS bypass. */
public final class BrowserActivity extends LeoActivity {
    private String current="leo:home",title=L.t("Browser text");
    private Spanned document;
    private ScrollView scroll;private TextView body;private View readingRoot;
    private int scrollY,linkIndex=-1,font=17,match=-1;private String search="";
    private final ArrayList<URLSpan> pageLinks=new ArrayList<URLSpan>();
    private final ArrayDeque<Page> trail=new ArrayDeque<Page>();
    private static final class Page {String url,title;Spanned text;int y,link;}
    private volatile int generation;
    private volatile HttpURLConnection connection;
    protected void onCreate(Bundle b){super.onCreate(b);font=Math.max(14,Math.min(26,getSharedPreferences("browser",0).getInt("font",17)));home();}
    protected void onDestroy(){generation++;HttpURLConnection c=connection;if(c!=null)c.disconnect();super.onDestroy();}
    private void home(){generation++;trail.clear();scrollY=0;linkIndex=-1;search="";match=-1;current="leo:home";title=L.t("Browser text");document=Html.fromHtml(L.t("<h2>Leo · Web în mod text</h2><p>0 adresă · 2 caută în pagină · 8 următorul rezultat. Stânga/dreapta sau 4/6 aleg linkul, 5 îl deschide. */# schimbă textul, 1/3 început/sfârșit.</p><p>Pagini simple: text HTML. Fără JavaScript, formulare web, video sau descărcări. Unele site-uri necesită un motor complet.</p><p><a href='https://example.org/'>Deschide example.org</a></p>"),Html.FROM_HTML_MODE_LEGACY);display();}
    private void display(){
        begin(title);readingRoot=root;scroll=new ScrollView(this);body=text("",font);body.setTextIsSelectable(false);scroll.addView(body);scroll.setFocusable(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        pageLinks.clear();for(URLSpan span:document.getSpans(0,document.length(),URLSpan.class))if(pageLinks.size()<150&&document.getSpanEnd(span)>document.getSpanStart(span)){try{resolveLink(span);pageLinks.add(span);}catch(Exception ignored){}}
        if(linkIndex>=pageLinks.size())linkIndex=-1;paintDocument();
        left=new Runnable(){public void run(){captureScroll();menu();}};footer(L.t("Opțiuni"),"4 / 6 linkuri",L.t("Înapoi"));scroll.requestFocus();
        final int y=scrollY;final ScrollView target=scroll;target.post(new Runnable(){public void run(){if(scroll==target&&root==readingRoot)target.scrollTo(0,y);}});
        back=new Runnable(){public void run(){backPage();}};
    }
    private String resolveLink(URLSpan span)throws IOException{return current.equals("leo:home")?checked(span.getURL()).toExternalForm():BrowserRules.resolve(current,span.getURL());}
    private void captureScroll(){if(root==readingRoot&&scroll!=null)scrollY=scroll.getScrollY();}
    private void rememberPage(){
        if(document==null)return;Page page=new Page();page.url=current;page.title=title;page.text=document;page.y=scrollY;page.link=linkIndex;trail.addLast(page);
        int total=0;for(Page p:trail)total+=p.text.length();while(trail.size()>3||total>262144){Page old=trail.removeFirst();total-=old.text.length();}
    }
    private void backPage(){
        generation++;HttpURLConnection c=connection;if(c!=null)c.disconnect();
        if(!trail.isEmpty()){Page page=trail.removeLast();current=page.url;title=page.title;document=page.text;scrollY=page.y;linkIndex=page.link;match=-1;search="";display();}
        else if(!current.equals("leo:home"))home();else finish();
    }
    private void paintDocument(){
        SpannableString painted=new SpannableString(document);int start=-1,end=-1;
        if(match>=0&&match+search.length()<=document.length()){start=match;end=match+search.length();}
        else if(linkIndex>=0&&linkIndex<pageLinks.size()){URLSpan span=pageLinks.get(linkIndex);start=document.getSpanStart(span);end=document.getSpanEnd(span);}
        if(start>=0&&end>start){painted.setSpan(new BackgroundColorSpan(palette.selection),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);painted.setSpan(new ForegroundColorSpan(palette.ink),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
        body.setText(painted);
    }
    private void reveal(final int at){final TextView target=body;final ScrollView scroller=scroll;target.post(new Runnable(){public void run(){if(body!=target||root!=readingRoot)return;android.text.Layout layout=target.getLayout();if(layout!=null)scroller.smoothScrollTo(0,Math.max(0,layout.getLineTop(layout.getLineForOffset(Math.max(0,Math.min(at,document.length()))))-dp(24)));}});}
    private void selectLink(int step){if(pageLinks.isEmpty()){notice(L.t("Pagina nu are linkuri HTTP/HTTPS"));return;}match=-1;linkIndex=linkIndex<0?(step<0?pageLinks.size()-1:0):CircularNavigation.step(linkIndex,step,pageLinks.size());paintDocument();reveal(document.getSpanStart(pageLinks.get(linkIndex)));notice("Link "+(linkIndex+1)+" / "+pageLinks.size());}
    private void openLink(){if(linkIndex<0||linkIndex>=pageLinks.size()){selectLink(1);return;}try{fetch(resolveLink(pageLinks.get(linkIndex)));}catch(Exception e){notice(L.t("Link indisponibil"));}}
    private void zoom(int step){captureScroll();font=Math.max(14,Math.min(26,font+step));getSharedPreferences("browser",0).edit().putInt("font",font).apply();body.setTextSize(font*Appearance.textScale(this));if(linkIndex>=0)reveal(document.getSpanStart(pageLinks.get(linkIndex)));else{final int y=scrollY;body.post(new Runnable(){public void run(){scroll.scrollTo(0,y);}});}notice("Text · "+font);}
    private void searchPage(){LinearLayout f=fields(L.t("Caută în pagină"));final EditText q=field(f,"Text",search,InputType.TYPE_CLASS_TEXT);q.setFilters(new InputFilter[]{new InputFilter.LengthFilter(128)});left=new Runnable(){public void run(){search=q.getText().toString().trim();match=-1;display();nextMatch();}};back=new Runnable(){public void run(){display();}};footer(L.t("Caută"),"Editor",L.t("Șterge"));q.requestFocus();}
    private void nextMatch(){if(search.isEmpty()){searchPage();return;}match=BrowserRules.find(document.toString(),search,match<0?0:match+1);paintDocument();if(match>=0)reveal(match);else notice(L.t("Textul nu apare în pagină"));}
    public boolean onKeyDown(int key,KeyEvent e){
        if(root==readingRoot){
            if(key==KeyEvent.KEYCODE_4||key==KeyEvent.KEYCODE_6||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){if(e.getRepeatCount()==0)selectLink(key==KeyEvent.KEYCODE_4||key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1);return true;}
            if(key==KeyEvent.KEYCODE_5||key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){if(e.getRepeatCount()==0)openLink();return true;}
            if(key==KeyEvent.KEYCODE_STAR||key==KeyEvent.KEYCODE_POUND){if(e.getRepeatCount()==0)zoom(key==KeyEvent.KEYCODE_STAR?-1:1);return true;}
            if(key==KeyEvent.KEYCODE_1||key==KeyEvent.KEYCODE_3){if(e.getRepeatCount()==0){scroll.fullScroll(key==KeyEvent.KEYCODE_1?View.FOCUS_UP:View.FOCUS_DOWN);}return true;}
            if(key==KeyEvent.KEYCODE_2){if(e.getRepeatCount()==0){captureScroll();searchPage();}return true;}
            if(key==KeyEvent.KEYCODE_8){if(e.getRepeatCount()==0)nextMatch();return true;}
            if(key==KeyEvent.KEYCODE_0){if(e.getRepeatCount()==0){captureScroll();address();}return true;}
        }
        return super.onKeyDown(key,e);
    }
    private void menu(){options(L.t("Browser text"),new String[]{L.t("Adresă / căutare"),L.t("Linkuri în pagină"),L.t("Adaugă la favorite"),L.t("Favorite"),L.t("Istoric"),L.t("Reîncarcă"),L.t("Pagina de start"),L.t("Caută în pagină · 2"),L.t("Următorul rezultat · 8"),L.t("Text mai mare · #"),L.t("Text mai mic · *"),L.t("Înapoi în paginile deschise"),L.t("Ajutor taste")},new Choice(){public void pick(int i){if(i==7)searchPage();else if(i==8){display();nextMatch();}else if(i==9||i==10){display();zoom(i==9?1:-1);}else if(i==11)backPage();else if(i==12)notice(L.t("0 adresă · 2 caută · 8 rezultat · 4/6 link · 5 deschide · */# text · 1/3 început/sfârșit · D-pad derulează"));else if(i==0)address();else if(i==1)links();else if(i==2){if(current.startsWith("http")){remember("bookmarks",30);notice(L.t("Favorit salvat"));}else notice(L.t("Deschide întâi o pagină web"));}else if(i==3||i==4)stored(i==3?"bookmarks":"history");else if(i==5){if(current.equals("leo:home"))home();else fetch(current);}else home();}});}
    private void address(){LinearLayout p=fields(L.t("Adresă / căutare"));final EditText value=field(p,L.t("URL sau cuvinte"),current.startsWith("http")?current:"",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);left=new Runnable(){public void run(){String s=value.getText().toString().trim();if(s.isEmpty())return;try{fetch(BrowserRules.address(s));}catch(Exception e){notice(L.t("Adresă invalidă"));}}};back=new Runnable(){public void run(){display();}};footer(L.t("Deschide"),"",L.t("Șterge"));value.requestFocus();}
    private static URL checked(String value)throws IOException{return BrowserRules.checked(value);}
    private void fetch(final String destination){
        try{checked(destination);}catch(IOException e){notice(L.t("Adresă HTTP/HTTPS invalidă"));return;}
        captureScroll();final int token=++generation;final HttpURLConnection old=connection;if(old!=null)old.disconnect();
        begin(L.t("Browser text"));root.addView(text(L.t("Se încarcă…\nÎnapoi: anulează"),18),new LinearLayout.LayoutParams(-1,0,1));back=new Runnable(){public void run(){generation++;HttpURLConnection c=connection;if(c!=null)c.disconnect();display();}};footer("","",L.t("Anulează"));
        worker.execute(new Runnable(){public void run(){try{
            URL url=checked(destination);String html=null;
            for(int redirects=0;redirects<=5;redirects++){
                if(Thread.currentThread().isInterrupted()||token!=generation)return;
                HttpURLConnection c=(HttpURLConnection)url.openConnection();connection=c;c.setInstanceFollowRedirects(false);c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setRequestProperty("User-Agent","LeoText/1.0 (Android 8.1; text reader)");c.setRequestProperty("Accept","text/html,text/plain;q=0.9");
                try{
                    int status=c.getResponseCode();if(status>=300&&status<400){String location=c.getHeaderField("Location");if(location==null||redirects==5)throw new IOException(L.t("Redirecționare invalidă"));URL next=BrowserRules.redirect(url,location);url=next;continue;}
                    if(status<200||status>=300)throw new IOException("HTTP "+status);
                    String type=c.getContentType();if(type==null)type="text/html";if(!(type.toLowerCase(Locale.ROOT).startsWith("text/html")||type.toLowerCase(Locale.ROOT).startsWith("text/plain")))throw new IOException(L.t("Format nesuportat; folosește o pagină text"));
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(token!=generation||Thread.currentThread().isInterrupted())return;if(bytes.size()+n>1048576)throw new IOException(L.t("Pagina depășește 1 MiB"));bytes.write(buf,0,n);}}
                    String encoding="UTF-8";Matcher m=Pattern.compile("charset\\s*=\\s*[\"']?([^;\\s\"']+)",Pattern.CASE_INSENSITIVE).matcher(type);if(m.find()&&Charset.isSupported(m.group(1)))encoding=m.group(1);
                    html=new String(bytes.toByteArray(),encoding);if(type.toLowerCase(Locale.ROOT).startsWith("text/plain"))html="<pre>"+TextUtils.htmlEncode(html)+"</pre>";break;
                }finally{c.disconnect();if(connection==c)connection=null;}
            }
            if(html==null)throw new IOException(L.t("Pagină goală"));
            String clean=html.replaceAll("(?is)<(script|style|noscript)\\b[^>]*>.*?</\\1\\s*>","");
            final Spanned parsed=Html.fromHtml(clean,Html.FROM_HTML_MODE_LEGACY);if(parsed.length()>262144)throw new IOException(L.t("Textul depășește 256 KiB de caractere"));final String loaded=url.toString();
            ui(new Runnable(){public void run(){if(token!=generation)return;if(!loaded.equals(current))rememberPage();current=loaded;title=checkedTitle(loaded);document=parsed;scrollY=0;linkIndex=-1;match=-1;search="";remember("history",50);display();}});
        }catch(final Exception e){ui(new Runnable(){public void run(){if(token!=generation)return;display();notice(L.t("Pagina nu poate fi citită: ")+(e instanceof javax.net.ssl.SSLException?"TLS / certificat incompatibil":e.getMessage()));}});}}});
    }
    private static String checkedTitle(String url){try{return new URL(url).getHost();}catch(Exception e){return L.t("Browser text");}}
    private void links(){final ArrayList<String> urls=new ArrayList<String>(),labels=new ArrayList<String>();for(URLSpan span:document.getSpans(0,document.length(),URLSpan.class)){if(urls.size()>=150)break;try{String u=resolveLink(span);urls.add(u);labels.add(document.subSequence(document.getSpanStart(span),document.getSpanEnd(span)).toString()+"\n"+u);}catch(Exception e){try{String u=checked(span.getURL()).toString();urls.add(u);labels.add(u);}catch(Exception ignored){}}}
        list(L.t("Linkuri · ")+urls.size(),labels,0,new Choice(){public void pick(int i){fetch(urls.get(i));}},"",null);back=new Runnable(){public void run(){display();}};
    }
    private JSONArray records(String key){try{return new JSONArray(getSharedPreferences("browser",0).getString(key,"[]"));}catch(JSONException e){return new JSONArray();}}
    private void remember(String key,int limit){JSONArray old=records(key),next=new JSONArray();try{next.put(new JSONObject().put("url",current).put("title",title));for(int i=0;i<old.length()&&next.length()<limit;i++){JSONObject item=old.getJSONObject(i);if(!current.equals(item.optString("url")))next.put(item);}getSharedPreferences("browser",0).edit().putString(key,next.toString()).apply();}catch(JSONException ignored){}}
    private void stored(final String key){final JSONArray saved=records(key);ArrayList<String> labels=new ArrayList<String>();for(int i=0;i<saved.length();i++){JSONObject o=saved.optJSONObject(i);labels.add(o==null?L.t("Intrare invalidă"):o.optString("title")+"\n"+o.optString("url"));}list(key.equals("history")?L.t("Istoric"):L.t("Favorite web"),labels,0,new Choice(){public void pick(int i){JSONObject o=saved.optJSONObject(i);if(o!=null)fetch(o.optString("url"));}},L.t("Șterge lista"),new Runnable(){public void run(){options(L.t("Ștergi lista?"),new String[]{L.t("Păstrează"),L.t("Șterge")},new Choice(){public void pick(int i){if(i==1){getSharedPreferences("browser",0).edit().remove(key).apply();stored(key);}}});}});back=new Runnable(){public void run(){display();}};}
}
