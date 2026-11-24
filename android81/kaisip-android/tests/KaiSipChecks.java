package ro.n0d3.kaisip;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** No account registration, network calls, microphone capture or audible playback. */
public final class KaiSipChecks extends Instrumentation {
    private int checks;
    private MainActivity page;
    private SipService service;
    private WebView web;
    private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    private Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    private String js(final String code)throws Exception{
        final AtomicReference<String> result=new AtomicReference<String>();final CountDownLatch done=new CountDownLatch(1);
        runOnMainSync(new Runnable(){public void run(){web.evaluateJavascript(code,new ValueCallback<String>(){public void onReceiveValue(String value){result.set(value);done.countDown();}});}});
        if(!done.await(8,TimeUnit.SECONDS))throw new AssertionError("JavaScript timeout");return result.get();
    }
    private void press(final int code,final int scan){ui(new Runnable(){public void run(){long now=SystemClock.uptimeMillis();page.dispatchKeyEvent(new KeyEvent(now,now,0,code,0,0,1,scan));page.dispatchKeyEvent(new KeyEvent(now,now+30,1,code,0,0,1,scan));}});SystemClock.sleep(120);}
    public void onCreate(Bundle args){start();}
    public void onStart(){Bundle result=new Bundle();PowerManager.WakeLock awake=null;
        try{
            getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            awake=((PowerManager)getTargetContext().getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"kaisip:local-checks");awake.acquire(120000);
            check(getTargetContext().checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_DENIED,"microphone remains ungranted");
            page=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            for(int i=0;i<100;i++){service=(SipService)field(page,"session");if(service!=null){web=(WebView)field(service,"web");if("true".equals(js("typeof AndroidPlatform==='object'&&!!document.getElementById('loginsrv')")))break;}SystemClock.sleep(100);}
            check(web!=null&&"true".equals(js("typeof AndroidPlatform==='object'&&!!document.getElementById('loginsrv')")),"bundled upstream UI boots with adapter");
            check("true".equals(js("Config.get().background==='off'&&!Config.isComplete()&&Sip.state().state==='unconfigured'")),"fresh app has no account or permanent connection");
            check(!((PowerManager.WakeLock)field(service,"cpu")).isHeld(),"fresh app holds no CPU lock");
            check("true".equals(js("window.isSecureContext&&typeof RTCPeerConnection==='function'&&!!navigator.mediaDevices&&typeof navigator.mediaDevices.getUserMedia==='function'")),"secure local origin exposes WebRTC audio APIs");
            check(SipService.trusted(android.net.Uri.parse(SipService.PAGE))&&!SipService.trusted(android.net.Uri.parse("https://example.invalid"))&&!SipService.trusted(android.net.Uri.parse("https://kaisip.keyshell.invalid:444/")),"native bridge origin boundary");
            check("true".equals(js("document.getElementById('loginsrv').readOnly&&document.getElementById('sk-c').textContent==='Editează'")),"keypad field offers native edit");
            check("Server".equals(new org.json.JSONObject(service.editor).getString("label")),"native editor uses the visible field label");
            press(KeyEvent.KEYCODE_DPAD_CENTER,352);
            final Dialog editor=(Dialog)field(page,"editor");check(editor!=null&&editor.isShowing(),"complete OK opens native editor");
            ui(new Runnable(){public void run(){((EditText)editor.getCurrentFocus()).setText("pbx.example.invalid");long now=SystemClock.uptimeMillis();editor.dispatchKeyEvent(new KeyEvent(now,now,0,KeyEvent.KEYCODE_MENU,0,0,1,139));editor.dispatchKeyEvent(new KeyEvent(now,now+20,1,KeyEvent.KEYCODE_MENU,0,0,1,139));}});
            SystemClock.sleep(150);check("true".equals(js("document.getElementById('loginsrv').value==='pbx.example.invalid'&&!Config.isComplete()")),"left softkey commits field without registration");
            press(KeyEvent.KEYCODE_DPAD_CENTER,352);check("true".equals(js("document.activeElement.id==='loginext'")),"next OK advances original account flow");
            press(KeyEvent.KEYCODE_BACK,14);check(field(page,"editor")==null,"dedicated Back cancels HTML flow without opening editor");
            check(!((Boolean)field(service,"callActive"))&&!((Boolean)field(service,"continuous")),"navigation made no call or persistent session");
            check("true".equals(js("typeof RTCPeerConnection.prototype.addTrack==='function'&&typeof RTCPeerConnection.prototype.getSenders==='function'")),"legacy WebView exposes the track/sender APIs required by JsSIP");
            js("window.localRtc='pending';(function(){var a=new (window.AudioContext||window.webkitAudioContext)(),d=a.createMediaStreamDestination(),p=new RTCPeerConnection({iceServers:[]});d.stream.getTracks().forEach(function(t){p.addTrack(t,d.stream);});p.createOffer().then(function(o){window.localRtc=/m=audio/.test(o.sdp)&&/opus/i.test(o.sdp)?'pass':'missing audio';p.close();a.close();},function(){window.localRtc='failed';p.close();a.close();});}())");
            for(int i=0;i<40&&"\"pending\"".equals(js("window.localRtc"));i++)SystemClock.sleep(100);
            check("\"pass\"".equals(js("window.localRtc")),"synthetic local audio offer supports Opus without ICE or microphone");
            check(getTargetContext().checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_DENIED,"tests never requested microphone permission");
            js("UI.openLogin();");SystemClock.sleep(150);
            Bitmap shot=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();
            try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"kaisip-account.png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}finally{shot.recycle();}
            ui(new Runnable(){public void run(){page.finish();}});page=null;
            // Activity finish is asynchronous; observe onStop/unbind/onDestroy,
            // rather than assuming the launcher transition completes in 500 ms.
            for(int i=0;i<40&&field(service,"web")!=null;i++){SystemClock.sleep(100);waitForIdleSync();}
            check(field(service,"web")==null&&!((PowerManager.WakeLock)field(service,"cpu")).isHeld(),"closing default app destroys WebView and releases CPU");
            result.putString("result","PASS "+checks+" isolated Android KaiSIP checks; no external SIP or microphone");
        }catch(Throwable error){result.putString("error","after "+checks+" checks: "+android.util.Log.getStackTraceString(error));}
        finally{if(page!=null)ui(new Runnable(){public void run(){page.finish();}});if(awake!=null&&awake.isHeld())awake.release();}
        finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}
