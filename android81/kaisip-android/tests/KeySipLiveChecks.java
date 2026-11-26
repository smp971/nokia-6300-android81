package ro.n0d3.kaisip;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.*;
import android.webkit.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Explicit opt-in, one bounded call to the owner's previously chosen test extension 200. No credentials or media are logged. */
public final class KeySipLiveChecks extends Instrumentation {
    MainActivity page;SipService service;WebView web;boolean allow,owned;String phase="startup";
    Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    String js(final String code)throws Exception{
        final AtomicReference<String> result=new AtomicReference<String>();final CountDownLatch done=new CountDownLatch(1);
        runOnMainSync(new Runnable(){public void run(){web.evaluateJavascript(code,new ValueCallback<String>(){public void onReceiveValue(String value){result.set(value);done.countDown();}});}});
        if(!done.await(10,TimeUnit.SECONDS))throw new IOException("WebView did not respond");return result.get();
    }
    String text(String code)throws Exception{return new JSONArray("["+js(code)+"]").optString(0);}
    public void onCreate(Bundle args){allow=args!=null&&"true".equals(args.getString("allow_live"))&&"200".equals(args.getString("destination"));start();}
    public void onStart(){Bundle result=new Bundle();JSONObject report=new JSONObject();PowerManager.WakeLock awake=null;
        try{
            getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            if(!allow)throw new SecurityException("Live call requires explicit allow_live and destination 200");
            AudioManager audio=(AudioManager)getTargetContext().getSystemService(Context.AUDIO_SERVICE);
            if(audio.getMode()==AudioManager.MODE_IN_CALL)throw new IllegalStateException("Cellular call active; left untouched");
            if(getTargetContext().checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("Microphone permission not granted");
            awake=((PowerManager)getTargetContext().getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"keysip:bounded-call-check");awake.acquire(120000);
            page=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            for(int i=0;i<120;i++){service=(SipService)field(page,"session");if(service!=null){web=(WebView)field(service,"web");if("true".equals(js("typeof AndroidPlatform==='object'&&typeof Sip==='object'")))break;}SystemClock.sleep(100);}
            if(web==null||!"true".equals(js("Config.isComplete()")))throw new IllegalStateException("No configured SIP account; left untouched");
            for(int i=0;i<150&&!"registered".equals(text("Sip.state().state"));i++)SystemClock.sleep(100);
            report.put("registration",text("Sip.state().state"));
            if(!"registered".equals(text("Sip.state().state")))throw new IllegalStateException("Registration did not become ready");
            if("true".equals(js("Call.inCall()")))throw new IllegalStateException("Existing call left untouched");
            js("window.__keySipProbe={signals:[],states:[],responses:[],ice:[],failure:''};(function(){var p=window.__keySipProbe,send=WebSocket.prototype.send;WebSocket.prototype.send=function(value){var m=typeof value==='string'&&/^(INVITE|ACK|CANCEL|BYE) /.exec(value);if(m)p.signals.push(m[1]);return send.apply(this,arguments);};Call.on('change',function(c){if(c&&/^(calling|ringing|incoming|incall|ended)$/.test(c.state))p.states.push(c.state);});Sip.ua().on('newRTCSession',function(e){if(e.originator!=='local')return;var s=e.session;s.on('progress',function(v){if(v.response)p.responses.push(Number(v.response.status_code));});s.on('failed',function(v){p.failure=String(v.cause||'failed').replace(/[^A-Za-z0-9 _-]/g,'').slice(0,60);});s.on('peerconnection',function(v){v.peerconnection.addEventListener('iceconnectionstatechange',function(){p.ice.push(v.peerconnection.iceConnectionState);});});});}());");
            phase="home-dashboard";
            if(!"true".equals(js("!!document.querySelector('#screen .clock')&&!!document.querySelector('#screen .chips')&&!document.querySelector('.dialnum')")))throw new IllegalStateException("Empty home dashboard unavailable; no keys injected");
            phase="digits";
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_2);SystemClock.sleep(150);sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_0);SystemClock.sleep(150);sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_0);waitForIdleSync();SystemClock.sleep(150);
            if(!"true".equals(js("document.querySelector('.dialnum').textContent==='200'")))throw new IllegalStateException("Keypad number entry failed; no call started");
            phase="green-key";owned=true;
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_CALL);waitForIdleSync();SystemClock.sleep(200);
            if(!"true".equals(js("!!Call.current()&&Call.current().number==='200'")))throw new IllegalStateException("Green key did not start test extension 200");
            report.put("dial_control","keypad-2-0-0-Call");phase="connection";
            long until=SystemClock.elapsedRealtime()+25000;String state="";
            while(SystemClock.elapsedRealtime()<until){state=text("Call.current()?Call.current().state:'none'");if(state.equals("incall")||state.equals("ended"))break;SystemClock.sleep(250);}
            report.put("state",state).put("audio_mode",audio.getMode()).put("speaker",audio.isSpeakerphoneOn());
            if(state.equals("incall")){
                js("Route.setSpeaker(true)");SystemClock.sleep(300);report.put("speaker_switch",audio.isSpeakerphoneOn());
                js("Route.setSpeaker(false)");SystemClock.sleep(300);report.put("earpiece_switch",!audio.isSpeakerphoneOn());
                js("window.__keySipStats={pending:true};(function(){var c=Call.current(),pc=c&&c.session&&c.session.connection;if(!pc||!pc.getStats){window.__keySipStats={supported:false};return;}pc.getStats().then(function(items){var r={sent:0,received:0,packetsSent:0,packetsReceived:0};items.forEach(function(v){if(v.type==='outbound-rtp'){r.sent+=Number(v.bytesSent||0);r.packetsSent+=Number(v.packetsSent||0);}if(v.type==='inbound-rtp'){r.received+=Number(v.bytesReceived||0);r.packetsReceived+=Number(v.packetsReceived||0);}});window.__keySipStats=r;},function(){window.__keySipStats={failed:true};});}());");
                for(int i=0;i<30&&"true".equals(js("window.__keySipStats.pending===true"));i++)SystemClock.sleep(100);
                report.put("rtp",new JSONObject(js("window.__keySipStats")));
            }
            report.put("stages",new JSONObject(js("window.__keySipProbe")));
            result.putString("result",state.equals("incall")?"CONNECTED test extension 200; human audibility not measured":"DIAGNOSED test extension 200; see sanitized report");
        }catch(Throwable error){try{report.put("test_error",error.getClass().getSimpleName()).put("phase",phase);}catch(JSONException ignored){}result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{
            try{if(owned&&web!=null){js("if(Call.current()&&Call.current().number==='200'&&Call.inCall())Call.hangup()");SystemClock.sleep(1000);js("Route.end()");}if(page!=null){runOnMainSync(new Runnable(){public void run(){page.finish();}});}if(awake!=null&&awake.isHeld())awake.release();}catch(Exception e){result.putString("cleanup",e.getClass().getSimpleName());}
            try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"keysip-call-check.json"))){out.write(report.toString().getBytes("UTF-8"));}catch(IOException ignored){}
        }
        result.putString("diagnostics",report.toString());finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}
