package ro.leo.visual;

import android.app.DownloadManager;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.text.InputType;
import android.webkit.*;
import android.widget.*;
import java.io.IOException;
import ro.leo.i18n.L;

/** One graphical web page in the existing WebView process, with native keypad chrome. */
public final class WebBrowserActivity extends LeoActivity {
    private WebView web;
    private ProgressBar progress;
    private TextView location;
    private WebCursorView cursor;
    private String current="https://example.org/";
    private boolean javascript=true;
    private int zoom=120;
    private int editorType,editorAction,resumeX,resumeY;
    private boolean resumePending;

    protected void onCreate(Bundle state){
        super.onCreate(state);
        if(state!=null){current=state.getString("url",current);javascript=state.getBoolean("javascript",true);zoom=state.getInt("zoom",120);}
        else if(getIntent().getData()!=null)current=getIntent().getDataString();
        if(state==null)resumeRequest(getIntent());
        begin(L.t("Browser web"));location=subtitle("");location.setSingleLine(true);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);
        root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        try{
            web=new WebView(this){
                public InputConnection onCreateInputConnection(EditorInfo info){
                    InputConnection connection=super.onCreateInputConnection(info);
                    final int type=info.inputType,action=info.imeOptions&EditorInfo.IME_MASK_ACTION;
                    post(new Runnable(){public void run(){if(cursor==null||isFinishing()||isDestroyed())return;editorType=type;editorAction=action;if((type&InputType.TYPE_MASK_CLASS)!=InputType.TYPE_NULL)cursor.editorReady();cursorFooter();}});
                    return connection;
                }
            };web.setLayerType(View.LAYER_TYPE_SOFTWARE,null);web.setBackgroundColor(android.graphics.Color.WHITE);
            WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(javascript);settings.setDomStorageEnabled(true);
            settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);settings.setSupportZoom(true);
            settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);settings.setTextZoom(Math.round(zoom*Appearance.textScale(this)));
            settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setGeolocationEnabled(false);settings.setSupportMultipleWindows(false);
            CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
            web.setWebViewClient(new WebViewClient(){
                public boolean shouldOverrideUrlLoading(WebView view,String url){return !allowed(url);}
                public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !allowed(request.getUrl().toString());}
                public void onPageStarted(WebView view,String url,Bitmap icon){if(allowed(url)){current=url;location.setText(url);}progress.setVisibility(View.VISIBLE);}
                public void onPageFinished(WebView view,String url){progress.setVisibility(View.INVISIBLE);if(allowed(url)){current=url;location.setText(url);BrowserStore.remember(WebBrowserActivity.this,"history",url,view.getTitle());if(resumePending){resumePending=false;view.scrollTo(resumeX,resumeY);}savePage();}}
                public void doUpdateVisitedHistory(WebView view,String url,boolean reload){if(allowed(url)){current=url;location.setText(url);BrowserStore.remember(WebBrowserActivity.this,"history",url,view.getTitle());}}
                public void onReceivedSslError(WebView view,SslErrorHandler handler,SslError error){handler.cancel();notice(L.t("Certificatul paginii nu poate fi validat"));}
                public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame()){progress.setVisibility(View.INVISIBLE);notice(L.t("Pagina nu poate fi încărcată"));}}
            });
            web.setWebChromeClient(new WebChromeClient(){
                public void onProgressChanged(WebView view,int value){progress.setProgress(value);}
                public void onReceivedTitle(WebView view,String title){if(title!=null&&!title.trim().isEmpty()){headingTitle(title);if(allowed(current))BrowserStore.remember(WebBrowserActivity.this,"history",current,title);}}
                public void onPermissionRequest(PermissionRequest request){request.deny();}
                public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){callback.onReceiveValue(null);return true;}
            });
            web.setDownloadListener(new DownloadListener(){public void onDownloadStart(final String url,String agent,String disposition,String mime,long bytes){download(url);}});
            FrameLayout page=new FrameLayout(this);
            page.addView(web,new FrameLayout.LayoutParams(-1,-1));
            cursor=new WebCursorView(this,web,new WebCursorView.Listener(){public void changed(boolean enabled){if(enabled)editorType=0;cursorFooter();}});
            cursor.speed(BrowserStore.snapshot(this).getInt("cursor-speed",1));
            if(state!=null)cursor.restore(state.getBoolean("cursor",true),state.getFloat("cursor_x",.5f),state.getFloat("cursor_y",.5f));
            page.addView(cursor,new FrameLayout.LayoutParams(-1,-1));
            root.addView(page,new LinearLayout.LayoutParams(-1,0,1));
            left=new Runnable(){public void run(){menu();}};
            back=new Runnable(){public void run(){cursor.suspend();if(web.canGoBack())web.goBack();else finish();}};
            cursorFooter();
            if(state==null||web.restoreState(state)==null)load(current);
            if(cursor.enabled())cursor.requestFocus();else web.requestFocus();
        }catch(RuntimeException unavailable){errorPage(L.t("Browser web"),L.t("Motorul web nu poate porni"),new Runnable(){public void run(){recreate();}});}
    }
    static boolean allowed(String url){try{BrowserRules.checked(url);return true;}catch(IOException invalid){return false;}}
    private void load(String url){if(!allowed(url)){notice(L.t("Doar adrese HTTP / HTTPS"));return;}if(cursor!=null)cursor.suspend();current=url;location.setText(url);web.loadUrl(url);}
    protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);resumeRequest(intent);if(intent.getData()!=null&&web!=null)load(intent.getDataString());}
    private void resumeRequest(Intent intent){resumePending=intent.getBooleanExtra("resume_page",false);if(resumePending){Bundle page=BrowserStore.snapshot(this);resumeX=page.getInt("last-x",0);resumeY=page.getInt("last-y",0);}}
    private void savePage(){if(web!=null&&allowed(current))BrowserStore.resume(this,current,web.getTitle(),web.getScrollX(),web.getScrollY());}
    protected void onSaveInstanceState(Bundle state){state.putString("url",current);state.putInt("zoom",zoom);state.putBoolean("javascript",javascript);if(cursor!=null){state.putBoolean("cursor",cursor.enabled());state.putFloat("cursor_x",cursor.fractionX());state.putFloat("cursor_y",cursor.fractionY());}if(web!=null)web.saveState(state);super.onSaveInstanceState(state);}
    private String editorLabel(){
        switch(editorAction){
            case EditorInfo.IME_ACTION_SEARCH:return "Caută";
            case EditorInfo.IME_ACTION_SEND:return "Trimite";
            case EditorInfo.IME_ACTION_GO:return "Deschide";
            case EditorInfo.IME_ACTION_DONE:return "Gata";
            case EditorInfo.IME_ACTION_NEXT:return "Următor";
            default:return (editorType&InputType.TYPE_TEXT_FLAG_MULTI_LINE)!=0?"Rând nou":"Scrie";
        }
    }
    private void cursorFooter(){
        boolean editing=(editorType&InputType.TYPE_MASK_CLASS)!=InputType.TYPE_NULL&&!cursor.enabled();
        String center=cursor.enabled()?(cursor.scrolling()?"Cursor":"Clic"):editing?editorLabel():"Selectează";
        footer(L.t("Opțiuni"),L.t(center),L.t(editing?"Șterge":"Înapoi"));
    }
    private void address(){startActivity(new Intent(this,BrowserHubActivity.class).putExtra("edit_address",true).putExtra("address",current));}
    private void menu(){
        if(cursor!=null)cursor.suspend();
        options(L.t("Browser web"),new String[]{L.t(cursor.enabled()?"Cursor · pornit":"Cursor · oprit"),L.t("Adresă / căutare"),L.t("Reîncarcă"),L.t("Oprește încărcarea"),L.t("Text mai mare"),L.t("Text mai mic"),L.t("Înainte"),javascript?L.t("JavaScript · pornit"):L.t("JavaScript · oprit"),L.t("Mod text"),L.t("Ajutor"),L.t("Închide"),L.t("Adaugă la favorite"),L.t("Favorite"),L.t("Istoric"),L.t("Viteză cursor"),L.t("Următorul link / câmp"),L.t("Linkul / câmpul anterior")},new Choice(){public void pick(int row){
            if(row==11){BrowserStore.remember(WebBrowserActivity.this,"bookmarks",current,web.getTitle());notice(L.t("Favorit salvat"));return;}
            if(row==12||row==13){startActivity(new Intent(WebBrowserActivity.this,BrowserHubActivity.class).putExtra("show_list",row==12?"bookmarks":"history"));return;}
            if(row==14){options(L.t("Viteză cursor"),new String[]{L.t("Lentă"),L.t("Normală"),L.t("Rapidă")},new Choice(){public void pick(int at){BrowserStore.setting(WebBrowserActivity.this,"cursor-speed",at);cursor.speed(at);}});return;}
            if(row==15||row==16){cursor.navigate(row==16);return;}
            if(row==0){cursor.setCursorEnabled(!cursor.enabled());return;}int index=row-1;
            if(index==0)address();else if(index==1)web.reload();else if(index==2)web.stopLoading();
            else if(index==3||index==4){zoom=Math.max(80,Math.min(220,zoom+(index==3?10:-10)));web.getSettings().setTextZoom(Math.round(zoom*Appearance.textScale(WebBrowserActivity.this)));}
            else if(index==5){if(web.canGoForward())web.goForward();}
            else if(index==6){javascript=!javascript;web.getSettings().setJavaScriptEnabled(javascript);web.reload();}
            else if(index==7)startActivity(new Intent(WebBrowserActivity.this,BrowserActivity.class).setData(Uri.parse(current)));
            else if(index==9)finish();
            else options(L.t("Ajutor Browser"),new String[]{L.t("Săgeți · deplasează cursorul"),L.t("OK · clic pe pagină"),L.t("* · derulare / cursor"),L.t("În derulare: OK revine la cursor"),L.t("Margini sus / jos · derulare"),L.t("# · cursor pornit / oprit"),L.t("Câmp text · tastele scriu"),L.t("Tasta dreaptă · șterge în editor"),L.t("Săgeți stânga / dreapta · poziție în text"),L.t("Opțiuni → Cursor · revino la mouse"),L.t("Tasta Înapoi dedicată · pagina anterioară")},new Choice(){public void pick(int row){}});
        }});
    }
    private void download(final String url){
        if(!allowed(url)){notice(L.t("Adresă invalidă"));return;}
        options(L.t("Descarcă fișierul?"),new String[]{L.t("Anulează"),L.t("Descarcă")},new Choice(){public void pick(int index){if(index!=1)return;try{boolean wifi=getSharedPreferences("downloads",0).getBoolean("wifi",true);((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(DownloadsActivity.request(url,wifi));notice(L.t("Descărcare programată"));}catch(RuntimeException failed){notice(L.t("Descărcarea nu a putut fi programată"));}}});
    }
    protected void onResume(){super.onResume();if(web!=null){web.getSettings().setTextZoom(Math.round(zoom*Appearance.textScale(this)));web.onResume();}}
    public boolean dispatchKeyEvent(KeyEvent event){if(isJackNavigationNoise(event))return true;if(cursor!=null&&cursor.key(event))return true;return super.dispatchKeyEvent(event);}
    public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(!focus&&cursor!=null)cursor.suspend();}
    protected void onPause(){if(cursor!=null)cursor.suspend();if(web!=null){savePage();web.onPause();}super.onPause();}
    protected void onDestroy(){if(web!=null){web.stopLoading();if(web.getParent() instanceof ViewGroup)((ViewGroup)web.getParent()).removeView(web);web.destroy();web=null;}super.onDestroy();}
}
