package ro.leo.visual;

import android.app.DownloadManager;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.IOException;
import ro.leo.i18n.L;

/** One graphical web page in the existing WebView process, with native keypad chrome. */
public final class WebBrowserActivity extends LeoActivity {
    private WebView web;
    private ProgressBar progress;
    private TextView location;
    private String current="https://example.org/";
    private boolean javascript=true;
    private int zoom=120;

    protected void onCreate(Bundle state){
        super.onCreate(state);
        if(state!=null){current=state.getString("url",current);javascript=state.getBoolean("javascript",true);zoom=state.getInt("zoom",120);}
        else if(getIntent().getData()!=null)current=getIntent().getDataString();
        begin(L.t("Browser web"));location=subtitle("");location.setSingleLine(true);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);
        root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        try{
            web=new WebView(this);web.setLayerType(View.LAYER_TYPE_SOFTWARE,null);web.setBackgroundColor(android.graphics.Color.WHITE);
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
                public void onPageFinished(WebView view,String url){progress.setVisibility(View.INVISIBLE);if(allowed(url)){current=url;location.setText(url);}}
                public void onReceivedSslError(WebView view,SslErrorHandler handler,SslError error){handler.cancel();notice(L.t("Certificatul paginii nu poate fi validat"));}
                public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame()){progress.setVisibility(View.INVISIBLE);notice(L.t("Pagina nu poate fi încărcată"));}}
            });
            web.setWebChromeClient(new WebChromeClient(){
                public void onProgressChanged(WebView view,int value){progress.setProgress(value);}
                public void onReceivedTitle(WebView view,String title){if(title!=null&&!title.trim().isEmpty())headingTitle(title);}
                public void onPermissionRequest(PermissionRequest request){request.deny();}
                public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){callback.onReceiveValue(null);return true;}
            });
            web.setDownloadListener(new DownloadListener(){public void onDownloadStart(final String url,String agent,String disposition,String mime,long bytes){download(url);}});
            root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
            left=new Runnable(){public void run(){menu();}};
            back=new Runnable(){public void run(){if(web.canGoBack())web.goBack();else finish();}};
            footer(L.t("Opțiuni"),L.t("Selectează"),L.t("Înapoi"));
            if(state==null||web.restoreState(state)==null)load(current);
            web.requestFocus();
        }catch(RuntimeException unavailable){errorPage(L.t("Browser web"),L.t("Motorul web nu poate porni"),new Runnable(){public void run(){recreate();}});}
    }
    static boolean allowed(String url){try{BrowserRules.checked(url);return true;}catch(IOException invalid){return false;}}
    private void load(String url){if(!allowed(url)){notice(L.t("Doar adrese HTTP / HTTPS"));return;}current=url;location.setText(url);web.loadUrl(url);}
    protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getData()!=null&&web!=null)load(intent.getDataString());}
    protected void onSaveInstanceState(Bundle state){state.putString("url",current);state.putInt("zoom",zoom);state.putBoolean("javascript",javascript);if(web!=null)web.saveState(state);super.onSaveInstanceState(state);}
    private void address(){startActivity(new Intent(this,BrowserHubActivity.class).putExtra("edit_address",true).putExtra("address",current));}
    private void menu(){
        options(L.t("Browser web"),new String[]{L.t("Adresă / căutare"),L.t("Reîncarcă"),L.t("Oprește încărcarea"),L.t("Text mai mare"),L.t("Text mai mic"),L.t("Înainte"),javascript?L.t("JavaScript · pornit"):L.t("JavaScript · oprit"),L.t("Mod text"),L.t("Ajutor"),L.t("Închide")},new Choice(){public void pick(int index){
            if(index==0)address();else if(index==1)web.reload();else if(index==2)web.stopLoading();
            else if(index==3||index==4){zoom=Math.max(80,Math.min(220,zoom+(index==3?10:-10)));web.getSettings().setTextZoom(Math.round(zoom*Appearance.textScale(WebBrowserActivity.this)));}
            else if(index==5){if(web.canGoForward())web.goForward();}
            else if(index==6){javascript=!javascript;web.getSettings().setJavaScriptEnabled(javascript);web.reload();}
            else if(index==7)startActivity(new Intent(WebBrowserActivity.this,BrowserActivity.class).setData(Uri.parse(current)));
            else if(index==9)finish();
            else options(L.t("Ajutor Browser"),new String[]{L.t("D-pad · selectare și derulare"),L.t("OK · deschide linkul selectat"),L.t("Meniu dedicat → Mouse din taste"),L.t("# · oprește mouse-ul"),L.t("Înapoi · pagina anterioară")},new Choice(){public void pick(int row){}});
        }});
    }
    private void download(final String url){
        if(!allowed(url)){notice(L.t("Adresă invalidă"));return;}
        options(L.t("Descarcă fișierul?"),new String[]{L.t("Anulează"),L.t("Descarcă")},new Choice(){public void pick(int index){if(index!=1)return;try{boolean wifi=getSharedPreferences("downloads",0).getBoolean("wifi",true);((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(DownloadsActivity.request(url,wifi));notice(L.t("Descărcare programată"));}catch(RuntimeException failed){notice(L.t("Descărcarea nu a putut fi programată"));}}});
    }
    protected void onResume(){super.onResume();if(web!=null){web.getSettings().setTextZoom(Math.round(zoom*Appearance.textScale(this)));web.onResume();}}
    protected void onPause(){if(web!=null)web.onPause();super.onPause();}
    protected void onDestroy(){if(web!=null){web.stopLoading();if(web.getParent() instanceof ViewGroup)((ViewGroup)web.getParent()).removeView(web);web.destroy();web=null;}super.onDestroy();}
}
