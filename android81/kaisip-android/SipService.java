package ro.n0d3.kaisip;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.*;
import org.json.JSONObject;

/** Isolated first-party WebRTC session. No root bridge or launcher privileges. */
public final class SipService extends Service {
    static final String HOST="kaisip.keyshell.invalid";
    static final String PAGE="https://"+HOST+"/index.html";
    private final Handler main=new Handler();
    private final LocalBinder binder=new LocalBinder();
    private WeakReference<MainActivity> owner=new WeakReference<MainActivity>(null);
    private MutableContextWrapper webContext;
    private WebView web;
    private boolean bound,continuous,callActive,foreground,audioOwned;
    volatile boolean editing;
    volatile String editor="{}";
    private int oldMode;
    private boolean oldSpeaker;
    private AudioManager audio;
    private PowerManager.WakeLock cpu;
    private final Set<Integer> locks=new HashSet<Integer>();
    private String registration="unconfigured";
    private long traceRead,traceUntil,traceSession;
    private int traceCount;
    private synchronized void trace(String stage,String value){
        if(!stage.matches("media|offer|answer|local|remote|candidate|gather|ice|signal|session|sdp|response|permission|script")
            ||!value.matches("[A-Za-z0-9_-]{1,40}"))return;
        long now=SystemClock.elapsedRealtime();
        if(now>=traceRead){
            traceRead=now+1000;
            try{
                long until=Long.parseLong((String)Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class).invoke(null,"sys.leo.siptrace.until","0"));
                if(until!=traceSession){traceSession=until;traceCount=0;}traceUntil=until;
            }catch(Exception unavailable){traceUntil=0;}
        }
        if(now<traceUntil&&traceUntil-now<=1200000&&traceCount++<256)
            android.util.Log.i("KaiSIPStage",stage+"="+value);
    }
    private final AudioManager.OnAudioFocusChangeListener focus=new AudioManager.OnAudioFocusChangeListener(){public void onAudioFocusChange(int change){
        if(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            script("AndroidPlatform.focusLost()");
    }};
    public final class LocalBinder extends Binder {SipService service(){return SipService.this;}}
    static boolean trusted(Uri uri){return uri!=null&&"https".equals(uri.getScheme())&&HOST.equals(uri.getHost())&&(uri.getPort()==-1||uri.getPort()==443);}
    public void onCreate(){
        super.onCreate();audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        cpu=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"kaisip:explicit-session");cpu.setReferenceCounted(false);
        NotificationManager notifications=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        notifications.createNotificationChannel(new NotificationChannel("session","Conexiune KaiSIP",NotificationManager.IMPORTANCE_LOW));
        createWebView();
    }
    public IBinder onBind(Intent intent){bound=true;return binder;}
    public boolean onUnbind(Intent intent){bound=false;if(!keep())stopSelf();return false;}
    public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&"stop".equals(intent.getAction())){
            continuous=false;callActive=false;locks.clear();script("AndroidPlatform.stop()");updateKeepAlive();stopSelf();
        }
        return START_NOT_STICKY;
    }
    private boolean keep(){return continuous||callActive||!locks.isEmpty();}
    void attach(MainActivity activity,FrameLayout parent){
        owner=new WeakReference<MainActivity>(activity);webContext.setBaseContext(activity);
        if(web.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)web.getParent()).removeView(web);
        parent.addView(web,new FrameLayout.LayoutParams(-1,-1));web.requestFocus();
    }
    void detach(MainActivity activity){
        if(owner.get()!=activity)return;
        if(web.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)web.getParent()).removeView(web);
        owner.clear();webContext.setBaseContext(getApplicationContext());
    }
    void script(final String code){main.post(new Runnable(){public void run(){if(web!=null)web.evaluateJavascript(code,null);}});}
    void key(String value,boolean down,boolean repeat){script("AndroidPlatform.key("+JSONObject.quote(value)+","+down+","+repeat+")");}
    private byte[] asset(String path)throws IOException{
        try(InputStream in=getAssets().open(path);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toByteArray();
        }
    }
    private WebResourceResponse response(String mime,byte[] data,int code){
        Map<String,String> headers=new HashMap<String,String>();
        headers.put("Cache-Control","no-store");
        headers.put("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; media-src 'self' blob:; connect-src wss:; frame-src 'none'; base-uri 'none'");
        return new WebResourceResponse(mime,"UTF-8",code,code==200?"OK":"Blocked",headers,new ByteArrayInputStream(data));
    }
    @SuppressWarnings("SetJavaScriptEnabled") private void createWebView(){
        webContext=new MutableContextWrapper(getApplicationContext());
        web=new WebView(webContext){public InputConnection onCreateInputConnection(EditorInfo info){
            InputConnection input=super.onCreateInputConnection(info);
            // HTML fields are edited in a native keypad dialog. This also keeps
            // the physical right softkey available for the app's own commands.
            info.inputType=android.text.InputType.TYPE_NULL;
            info.imeOptions|=EditorInfo.IME_FLAG_NO_EXTRACT_UI;
            return input==null?null:new InputConnectionWrapper(input,false){public boolean performEditorAction(int action){key("Enter",true,false);key("Enter",false,false);return true;}};
        }};
        // This port's legacy GPU driver crashes Chromium's accelerated renderer.
        // Match the already validated software rendering path of KeyShell Browser.
        web.setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        web.setBackgroundColor(Color.rgb(16,20,24));web.setFocusableInTouchMode(true);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMediaPlaybackRequiresUserGesture(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setUseWideViewPort(true);web.setInitialScale(100);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.addJavascriptInterface(new Platform(),"NativeKaiSIP");
        web.setWebViewClient(new WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !trusted(request.getUrl());}
            public boolean shouldOverrideUrlLoading(WebView view,String url){return !trusted(Uri.parse(url));}
            public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();String path=uri.getPath();
                if(!trusted(uri)||path==null||path.length()>512||path.contains("..")||path.indexOf('\0')>=0)return response("text/plain",new byte[0],403);
                try{
                    if(path.equals("/")||path.equals("/index.html")){
                        String html=new String(asset("upstream/index.html"),"UTF-8");
                        html=html.replace("<meta charset=\"utf-8\">","<meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=240,initial-scale=1,user-scalable=no\">");
                        html=html.replace("<script src=\"js/app.js\"></script>","<script src=\"/android-preflight.js\"></script><script src=\"/vendor/webrtc-adapter-6.4.8.js\"></script><script src=\"/android-platform.js\"></script><script src=\"js/app.js\"></script>");
                        return response("text/html",html.getBytes("UTF-8"),200);
                    }
                    String file=path.equals("/android-platform.js")||path.equals("/android-preflight.js")||path.equals("/vendor/webrtc-adapter-6.4.8.js")?path.substring(1):"upstream"+path;
                    String mime=path.endsWith(".js")?"application/javascript":path.endsWith(".css")?"text/css":path.endsWith(".png")?"image/png":"application/octet-stream";
                    return response(mime,asset(file),200);
                }catch(IOException unavailable){return response("text/plain",new byte[0],404);}
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            public void onPermissionRequest(final PermissionRequest request){main.post(new Runnable(){public void run(){
                MainActivity activity=owner.get();boolean microphone=false;
                for(String resource:request.getResources())if(PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource))microphone=true;
                if(activity!=null&&trusted(request.getOrigin())&&microphone)activity.requestMicrophone(request);else request.deny();
            }});}
            public boolean onConsoleMessage(ConsoleMessage message){return true;}
            public void onCloseWindow(WebView window){MainActivity activity=owner.get();if(activity!=null)activity.finish();}
        });
        web.loadUrl(PAGE);
    }
    private void updateKeepAlive(){
        boolean keep=keep();
        if(keep){
            startService(new Intent(this,SipService.class));
            Intent open=new Intent(this,MainActivity.class);PendingIntent pending=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,SipService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notification=new Notification.Builder(this,"session").setSmallIcon(android.R.drawable.sym_action_call).setContentTitle("KaiSIP")
                .setContentText(callActive?"Apel SIP în curs":"Conexiune în fundal · "+registration).setContentIntent(pending).setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Oprește",stop).build();
            startForeground(17,notification);foreground=true;if(!cpu.isHeld())cpu.acquire();
        }else{
            if(cpu.isHeld())cpu.release();if(foreground){stopForeground(true);foreground=false;}
            if(!bound)stopSelf();
        }
    }
    private void route(boolean begin,boolean speaker){
        if(begin){
            if(audio.getMode()==AudioManager.MODE_IN_CALL)return;
            if(!audioOwned){
                int mode=audio.getMode();boolean speakerWasOn=audio.isSpeakerphoneOn();
                if(audio.requestAudioFocus(focus,AudioManager.STREAM_VOICE_CALL,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)return;
                oldMode=mode;oldSpeaker=speakerWasOn;audioOwned=true;
            }
            audio.setMode(AudioManager.MODE_IN_COMMUNICATION);audio.setSpeakerphoneOn(speaker);
        }else if(audioOwned){
            audioOwned=false;
            // Restore only our communication mode. A cellular call may have
            // taken audio ownership; querying Telecom would require an extra
            // phone-state permission and cannot be assumed to succeed here.
            if(audio.getMode()==AudioManager.MODE_IN_COMMUNICATION){audio.setSpeakerphoneOn(oldSpeaker);audio.setMode(oldMode);}
            audio.abandonAudioFocus(focus);
        }
    }
    private final class Platform {
        @JavascriptInterface public void trace(String stage,String value){if(stage!=null&&value!=null)SipService.this.trace(stage,value);}
        @JavascriptInterface public void editing(boolean value){editing=value;}
        @JavascriptInterface public void editor(String value){if(value.length()<=16384)editor=value;}
        @JavascriptInterface public void background(final boolean value){main.post(new Runnable(){public void run(){continuous=value;updateKeepAlive();}});}
        @JavascriptInterface public void cpu(final int token,final boolean acquire){main.post(new Runnable(){public void run(){if(acquire){if(locks.size()<16)locks.add(token);}else locks.remove(token);updateKeepAlive();}});}
        @JavascriptInterface public void call(final boolean active){main.post(new Runnable(){public void run(){callActive=active;updateKeepAlive();}});}
        @JavascriptInterface public void state(final String value){main.post(new Runnable(){public void run(){registration=value.matches("[a-z]{1,20}")?value:"unknown";if(foreground)updateKeepAlive();}});}
        @JavascriptInterface public void route(final boolean begin,final boolean speaker){main.post(new Runnable(){public void run(){SipService.this.route(begin,speaker);}});}
        @JavascriptInterface public boolean headphones(){return audio.isWiredHeadsetOn()||audio.isBluetoothScoOn();}
        @JavascriptInterface public void foreground(){main.post(new Runnable(){public void run(){startActivity(new Intent(SipService.this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));}});}
        @JavascriptInterface public void close(){main.post(new Runnable(){public void run(){MainActivity activity=owner.get();if(activity!=null)activity.finish();}});}
    }
    public void onDestroy(){
        route(false,false);locks.clear();if(cpu.isHeld())cpu.release();stopForeground(true);
        if(web!=null){web.removeJavascriptInterface("NativeKaiSIP");web.stopLoading();web.destroy();web=null;}
        owner.clear();super.onDestroy();
    }
}
