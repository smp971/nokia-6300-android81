package ro.leo.visual;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.EditText;
import java.lang.reflect.*;
import java.util.*;

/** Key holds and dialer preparation only. Never selects a SIM or sends a service code. */
public final class HomeDialTests extends Instrumentation {
    private int checks;
    private Activity current;
    private volatile Activity resumed;
    @Override public void callActivityOnResume(Activity activity){super.callActivityOnResume(activity);resumed=activity;}
    private final List<String> actions=new ArrayList<String>();
    private HomeKeyPress keys;
    private void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    private void ui(final Runnable action){final Throwable[] failure={null};runOnMainSync(new Runnable(){public void run(){try{action.run();}catch(Throwable e){failure[0]=e;}}});if(failure[0]!=null)throw new RuntimeException(failure[0]);waitForIdleSync();}
    private Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private KeyEvent event(long stamp,int action,int key,int repeat){return new KeyEvent(stamp,SystemClock.uptimeMillis(),action,key,repeat);}
    private void close(){if(current!=null){ui(new Runnable(){public void run(){KeypadDialog d=KeypadDialog.current();if(d!=null)d.dismiss();current.finish();}});current=null;}}
    private void timing(){
        ui(new Runnable(){public void run(){keys=new HomeKeyPress(new Handler(),new HomeKeyPress.Actions(){public void shortPress(int key){actions.add("tap:"+key);}public void longPress(int key){actions.add("hold:"+key);}});}});
        for(final int key:new int[]{KeyEvent.KEYCODE_0,KeyEvent.KEYCODE_1,KeyEvent.KEYCODE_5,KeyEvent.KEYCODE_STAR,KeyEvent.KEYCODE_POUND}){
            final long stamp=SystemClock.uptimeMillis();ui(new Runnable(){public void run(){keys.down(event(stamp,0,key,0));}});check(actions.isEmpty(),"down alone does not run short action "+key);
            ui(new Runnable(){public void run(){keys.up(event(stamp,1,key,0));}});check(actions.equals(Arrays.asList("tap:"+key)),"release runs exactly one short action "+key);actions.clear();
        }
        final long held=SystemClock.uptimeMillis();ui(new Runnable(){public void run(){keys.down(event(held,0,KeyEvent.KEYCODE_POUND,0));}});SystemClock.sleep(HomeKeyPress.HOLD_MS+120);waitForIdleSync();
        ui(new Runnable(){public void run(){keys.down(event(held,0,KeyEvent.KEYCODE_POUND,1));keys.down(event(held,0,KeyEvent.KEYCODE_POUND,2));keys.up(event(held,1,KeyEvent.KEYCODE_POUND,0));}});check(actions.equals(Arrays.asList("hold:"+KeyEvent.KEYCODE_POUND)),"hold fires once without hardware repeats; release does not open card");actions.clear();
        final long canceled=SystemClock.uptimeMillis();ui(new Runnable(){public void run(){keys.down(event(canceled,0,KeyEvent.KEYCODE_STAR,0));keys.cancel();keys.up(event(canceled,1,KeyEvent.KEYCODE_STAR,0));}});SystemClock.sleep(HomeKeyPress.HOLD_MS+50);check(actions.isEmpty(),"leaving Home cancels pending lock command");
    }
    private MainActivity home(){
        // Home is singleTask and may have been resumed before instrumentation starts.
        // Await its resume with a deadline; startActivitySync would wait for onCreate.
        ui(new Runnable(){public void run(){getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}});
        for(int i=0;i<100&&(!(resumed instanceof MainActivity)||resumed.isFinishing()||resumed.isDestroyed());i++)SystemClock.sleep(100);
        check(resumed instanceof MainActivity&&!resumed.isFinishing()&&!resumed.isDestroyed(),"bounded Home resume");
        current=resumed;final MainActivity home=(MainActivity)current;ui(new Runnable(){public void run(){home.onNewIntent(new Intent(Intent.ACTION_MAIN));}});return home;
    }
    private void route(final int key,boolean hold,Class<?> destination)throws Exception{
        final MainActivity home=home();ActivityMonitor monitor=addMonitor(destination.getName(),null,false);final long stamp=SystemClock.uptimeMillis();
        try{
            ui(new Runnable(){public void run(){home.dispatchKeyEvent(event(stamp,0,key,0));}});SystemClock.sleep(100);check(monitor.getHits()==0,"no premature route on key down "+key);
            if(hold)SystemClock.sleep(HomeKeyPress.HOLD_MS+100);
            ui(new Runnable(){public void run(){home.dispatchKeyEvent(event(stamp,1,key,0));}});
            Activity opened=monitor.waitForActivityWithTimeout(3500);check(opened!=null,"Home route "+key+" hold="+hold+" -> "+destination.getSimpleName());current=opened;
            if(!hold)check(String.valueOf(key-KeyEvent.KEYCODE_0).equals(((EditText)field(opened,"number")).getText().toString()),"short digit remains in number editor");
            close();
        }finally{removeMonitor(monitor);}
    }
    private void dialing()throws Exception{
        for(String code:new String[]{"*123#","*100#","*#43#","#43#","**21*+40123456789#","##21#"}){
            check(DialDestination.parse(code).equals(code),"service delimiters preserved "+code);
            check(DialDestination.uri(code).getFragment()==null&&DialDestination.uri(code).getSchemeSpecificPart().equals(code)&&DialDestination.uri(code).toString().endsWith("%23"),"hash encoded in tel path");
        }
        for(String bad:new String[]{"*123","**#","123#","*123%23","tel:*123#","*12,3#","*12;3#","*1\n2#","abc","000","12"})check(DialDestination.parse(bad).isEmpty(),"reject malformed or unsupported destination");
        check(DialDestination.parse("+40 (712) 345-678").equals("+40712345678"),"normal number formatting retained");
        current=startActivitySync(new Intent(getTargetContext(),PhoneActivity.class).putExtra("number","*123#").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final PhoneActivity phone=(PhoneActivity)current;
        check(((EditText)field(phone,"number")).getText().toString().equals("*123#"),"USSD enters native dial field intact");
        ui(new Runnable(){public void run(){long stamp=SystemClock.uptimeMillis();phone.dispatchKeyEvent(event(stamp,0,KeyEvent.KEYCODE_CALL,0));phone.dispatchKeyEvent(event(stamp,1,KeyEvent.KEYCODE_CALL,0));}});
        check(KeypadDialog.current()!=null,"Green prepares SIM picker; no request sent");
        ui(new Runnable(){public void run(){KeypadDialog.current().dismiss();}});close();
    }
    public void onCreate(Bundle args){start();}
    public void onStart(){Bundle result=new Bundle();PowerManager.WakeLock awake=null;try{
        getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        awake=((PowerManager)getTargetContext().getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"keyshell:home-dial-checks");awake.acquire(120000);
        timing();route(KeyEvent.KEYCODE_0,false,PhoneActivity.class);route(KeyEvent.KEYCODE_1,false,PhoneActivity.class);route(KeyEvent.KEYCODE_5,false,PhoneActivity.class);route(KeyEvent.KEYCODE_0,true,NowPlayingActivity.class);route(KeyEvent.KEYCODE_1,true,GlobalSearchActivity.class);dialing();
        result.putString("result","PASS "+checks+" Home hold and USSD preparation checks; no SIM selection, calls or operator requests");
    }catch(Throwable error){result.putString("error","after "+checks+" checks: "+android.util.Log.getStackTraceString(error));}finally{try{close();if(keys!=null)ui(new Runnable(){public void run(){keys.cancel();}});if(awake!=null&&awake.isHeld())awake.release();}catch(Throwable error){result.putString("cleanup_error",android.util.Log.getStackTraceString(error));}}
        finish(result.containsKey("error")||result.containsKey("cleanup_error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}
