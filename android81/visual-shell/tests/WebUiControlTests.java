package ro.leo.visual;

import android.app.*;
import android.content.*;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import android.telecom.*;
import android.telephony.*;
import android.widget.EditText;
import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;

/** Real remote input; fake phone transport. Never places a call, sends a message or reads private rows. */
public final class WebUiControlTests extends WebUiTests {
    boolean enable;Activity page;int fakeCalls,fakeSms;
    public void onCreate(Bundle args){enable=args!=null&&"true".equals(args.getString("enable_control"));start();}
    void status(int code,String method,String path,JSONObject data)throws Exception{check(api(method,path,data).startsWith("HTTP/1.1 "+code),"control boundary "+method+" "+path+" "+code);}
    JSONObject body(String response)throws Exception{return new JSONObject(response.substring(response.indexOf("\r\n\r\n")+4));}
    void controls()throws Exception {
        check(!CompanionSettings.feature(fixture,"remote")&&!CompanionSettings.feature(fixture,"communication"),"new capabilities default off");
        check(request("GET","/api/remote/screen",null,null,null).startsWith("HTTP/1.1 401"),"screen requires authentication");
        status(403,"GET","/api/remote/info",null);status(403,"GET","/api/phone/state",null);
        fixture.getSharedPreferences(CompanionSettings.PREFS,0).edit().putBoolean("feature-remote",true).putBoolean("feature-communication",true).commit();
        status(200,"GET","/remote.js",null);status(200,"GET","/api/remote/info",null);
        check(request("GET","/",null,null,null).indexOf("catalog.js")<0,"no packaged sample catalog requested");
        status(404,"GET","/catalog.js",null);
        check(!CompanionInputBridge.allowed(26,0)&&!CompanionInputBridge.allowed(82,0)&&!CompanionInputBridge.allowed(4,212),"root helper rejects unapproved key/scan combinations");
        status(400,"POST","/api/remote/key",new JSONObject().put("key","home;reboot"));
        status(400,"POST","/api/remote/key",new JSONObject().put("key","home").put("shell","id"));
        status(400,"POST","/api/remote/tap",new JSONObject().put("x",-1).put("y",0));
        status(400,"POST","/api/remote/tap",new JSONObject().put("x",240).put("y",0));
        status(400,"POST","/api/remote/text",new JSONObject().put("text",""));
        status(400,"POST","/api/remote/launch",new JSONObject().put("id",15));
        status(400,"POST","/api/remote/launch",new JSONObject().put("id",999));
        status(409,"POST","/api/phone/confirm",new JSONObject().put("ticket","expired"));
        status(409,"POST","/api/phone/action",new JSONObject().put("id","missing").put("action","hangup").put("value",""));
        status(400,"POST","/api/phone/prepare",new JSONObject().put("kind","call").put("number","*123#").put("slot",0).put("body",""));
        status(400,"POST","/api/phone/prepare",new JSONObject().put("kind","sms").put("number","200").put("slot",0).put("body",""));
        status(400,"POST","/api/phone/prepare",new JSONObject().put("kind","call").put("number","200").put("slot",5).put("body",""));
        final CompanionCommunication fake=new CompanionCommunication(fixture,new CompanionCommunication.Transport(){
            public void call(Context c,String number,PhoneAccountHandle account){fakeCalls++;}
            public String sms(Context c,SmsManager manager,String number,String text){fakeSms++;return "synthetic";}
        });
        List<SubscriptionInfo> sims=SubscriptionManager.from(fixture).getActiveSubscriptionInfoList();check(sims!=null&&!sims.isEmpty(),"test SIM available for resolve-only checks");
        int slot=sims.get(0).getSimSlotIndex();
        JSONObject call=new JSONObject().put("kind","call").put("number","200").put("slot",slot).put("body","");
        JSONObject prepared=fake.prepare("session-a",call);check(fakeCalls==0,"preparation never calls transport");
        JSONObject confirm=new JSONObject().put("ticket",prepared.getString("ticket"));
        try{fake.confirm("session-b",confirm);check(false,"wrong session accepted");}catch(IllegalStateException expected){check(fakeCalls==0,"ticket bound to browser session");}
        fake.confirm("session-a",confirm);check(fakeCalls==1,"one fake call dispatched after confirmation");
        try{fake.confirm("session-a",confirm);check(false,"duplicate accepted");}catch(IllegalStateException expected){check(fakeCalls==1,"call replay refused");}
        JSONObject sms=fake.prepare("session-a",new JSONObject().put("kind","sms").put("number","200").put("slot",slot).put("body","Test local · șț"));check(fakeSms==0&&sms.getInt("segments")>0,"Unicode SMS budget without sending");
        fake.confirm("session-a",new JSONObject().put("ticket",sms.getString("ticket")));check(fakeSms==1,"one synthetic SMS transport call");
        try{fake.confirm("session-a",new JSONObject().put("ticket",sms.getString("ticket")));check(false,"SMS replay accepted");}catch(IllegalStateException expected){check(fakeSms==1,"SMS replay refused");}
        JSONObject cleared=fake.prepare("session-a",call);fake.clear();try{fake.confirm("session-a",new JSONObject().put("ticket",cleared.getString("ticket")));check(false,"cleared ticket accepted");}catch(IllegalStateException expected){check(true,"server stop clears prepared operations");}
        JSONObject expired=fake.prepare("session-a",call);java.lang.reflect.Field waiting=CompanionCommunication.class.getDeclaredField("pending");waiting.setAccessible(true);Map queued=(Map)waiting.get(fake);((CompanionCommunication.Pending)queued.get(expired.getString("ticket"))).until=SystemClock.elapsedRealtime()-1;
        try{fake.confirm("session-a",new JSONObject().put("ticket",expired.getString("ticket")));check(false,"expired ticket accepted");}catch(IllegalStateException expected){check(fakeCalls==1,"expired confirmation has no side effect");}
        final int[] attempts={0};CompanionCommunication failing=new CompanionCommunication(fixture,new CompanionCommunication.Transport(){public void call(Context c,String n,PhoneAccountHandle h){attempts[0]++;throw new IllegalStateException("simulated ambiguous result");}public String sms(Context c,SmsManager m,String n,String b){throw new AssertionError();}});
        JSONObject once=failing.prepare("a",call),sendOnce=new JSONObject().put("ticket",once.getString("ticket"));
        try{failing.confirm("a",sendOnce);check(false,"transport failure expected");}catch(IllegalStateException expected){check(attempts[0]==1,"simulated transport failure after consumption");}
        try{failing.confirm("a",sendOnce);check(false,"ambiguous transport replay accepted");}catch(IllegalStateException expected){check(attempts[0]==1,"ambiguous result cannot cause automatic duplicate call");}
        JSONObject state=body(api("GET","/api/phone/state",null));check(state.has("sims")&&state.has("calls")&&state.has("smsPending"),"bounded phone state available");
        String first=api("GET","/api/remote/screen",null);check(first.startsWith("HTTP/1.1 200")&&first.contains("X-KeyShell-Interactive:"),"frame includes real geometry and screen state");
        java.util.regex.Matcher tag=java.util.regex.Pattern.compile("ETag: ([^\\r\\n]+)").matcher(first);check(tag.find(),"frame content identity returned");
        java.lang.reflect.Field captured=CompanionControl.class.getDeclaredField("captured");captured.setAccessible(true);captured.setLong(server.controls,SystemClock.elapsedRealtime()+5000);
        check(request("GET","/api/remote/screen",server.token,"If-None-Match: "+tag.group(1)+"\r\n",null).startsWith("HTTP/1.1 304"),"unchanged cached frame has no image payload");captured.setLong(server.controls,0);
        status(200,"POST","/api/remote/key",new JSONObject().put("key","wake"));
        java.lang.reflect.Field socket=CompanionInputSession.class.getDeclaredField("socket");socket.setAccessible(true);Object warmSocket=socket.get(server.controls.input);check(warmSocket!=null,"lazy input session opened");
        page=startActivitySync(new Intent(getTargetContext(),TerminalActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        // Paste text into an empty command field, without Enter: no shell command is executed.
        java.lang.reflect.Field command=TerminalActivity.class.getDeclaredField("command");command.setAccessible(true);final EditText field=(EditText)command.get(page);
        runOnMainSync(new Runnable(){public void run(){field.setText("");field.requestFocus();}});
        status(200,"POST","/api/remote/text",new JSONObject().put("text","WebUI șț test"));SystemClock.sleep(400);
        check(field.getText().toString().equals("WebUI șț test"),"Unicode paste reaches real Android editor");
        status(200,"POST","/api/remote/key",new JSONObject().put("key","softRight"));SystemClock.sleep(250);
        check(field.getText().toString().equals("WebUI șț tes"),"right softkey carries physical scan 158 and deletes once");
        check(socket.get(server.controls.input)==warmSocket,"paste and softkey reuse one input helper");
        runOnMainSync(new Runnable(){public void run(){field.setText("");field.setInputType(android.text.InputType.TYPE_CLASS_PHONE);((android.view.inputmethod.InputMethodManager)getTargetContext().getSystemService(Context.INPUT_METHOD_SERVICE)).restartInput(field);}});SystemClock.sleep(300);
        for(String digit:new String[]{"1","2","3","4","5","6"})status(200,"POST","/api/remote/key",new JSONObject().put("key",digit));SystemClock.sleep(300);
        check(field.getText().toString().equals("123456"),"rapid numeric input remains ordered and exact (length "+field.length()+")");
        runOnMainSync(new Runnable(){public void run(){field.setText("");page.finish();}});page=null;
        // Remove only the empty Calculator task created by our earlier launch fixture.
        for(ActivityManager.AppTask task:((ActivityManager)getTargetContext().getSystemService(Context.ACTIVITY_SERVICE)).getAppTasks()){ActivityManager.RecentTaskInfo info=task.getTaskInfo();if(info.baseActivity!=null&&info.baseActivity.getClassName().equals(CalculatorActivity.class.getName()))task.finishAndRemoveTask();}
        ActivityMonitor monitor=addMonitor(CalculatorActivity.class.getName(),null,false);
        status(200,"POST","/api/remote/launch",new JSONObject().put("id",4));Activity opened=waitForMonitorWithTimeout(monitor,4000);removeMonitor(monitor);check(opened instanceof CalculatorActivity,"remote launch reaches native Calculator");
        for(int i=0;i<80&&!opened.hasWindowFocus();i++)SystemClock.sleep(50);check(opened.hasWindowFocus(),"Calculator window ready before testing Back");waitForIdleSync();
        status(200,"POST","/api/remote/key",new JSONObject().put("key","back"));SystemClock.sleep(500);
        check(opened.isFinishing()||opened.isDestroyed(),"remote app Back finishes Calculator");
        status(200,"POST","/api/remote/key",new JSONObject().put("key","home"));
        CompanionAuth auth=new CompanionAuth(fixture);auth.add("owner","fixture-password","admin");auth.add("reader","viewer-password","viewer");server.close();
        server=new CompanionServer(fixture,InetAddress.getByName("127.0.0.1"),0,auth);server.start();bearer=auth.login("reader","viewer-password");
        status(403,"GET","/api/remote/screen",null);status(403,"GET","/api/phone/contacts",null);status(403,"GET","/api/phone/messages",null);status(403,"POST","/api/phone/confirm",confirm);
        bearer=auth.login("owner","fixture-password");fixture.getSharedPreferences(CompanionSettings.PREFS,0).edit().putBoolean("feature-remote",false).putBoolean("feature-communication",false).commit();
        status(403,"GET","/api/remote/screen",null);status(403,"GET","/api/phone/state",null);
        status(400,"POST","/api/logout",new JSONObject().put("unexpected",true));
        status(200,"POST","/api/logout",new JSONObject());
        status(401,"GET","/api/desktop",null);
    }
    public void onStart(){Bundle result=new Bundle();final Context real=getTargetContext();try{
        getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        awake=((PowerManager)real.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"keyshell:remote-checks");awake.acquire(180000);
        directory=new File(real.getCacheDir(),prefix);directory.mkdirs();
        fixture=new ContextWrapper(real){public Context getApplicationContext(){return this;}public File getFilesDir(){return directory;}public SharedPreferences getSharedPreferences(String name,int mode){groups.add(name);return real.getSharedPreferences(prefix+name,mode);}public File getDatabasePath(String name){return new File(directory,name);}public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory f){return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),f);}public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory f,DatabaseErrorHandler h){return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getPath(),f,h);}};
        server=new CompanionServer(fixture,InetAddress.getByName("127.0.0.1"),0,CompanionServer.token(),true);server.start();controls();result.putString("result","PASS "+checks+" remote-control checks; phone transport synthetic; no call or SMS sent");
    }catch(Throwable e){result.putString("error","after "+checks+" checks: "+android.util.Log.getStackTraceString(e));}finally{
        if(page!=null)runOnMainSync(new Runnable(){public void run(){page.finish();}});if(server!=null)server.close();for(String name:groups)real.deleteSharedPreferences(prefix+name);if(directory!=null)remove(directory);if(awake!=null&&awake.isHeld())awake.release();
        if(enable)real.getSharedPreferences(CompanionSettings.PREFS,0).edit().putBoolean("feature-remote",true).putBoolean("feature-communication",true).commit();CompanionService.ensureStarted(real);
    }
        try(FileOutputStream out=new FileOutputStream(new File(real.getFilesDir(),"webui-control-result.txt"))){out.write(result.toString().getBytes("UTF-8"));}catch(IOException ignored){}finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}
