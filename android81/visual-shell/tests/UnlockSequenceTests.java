package ro.leo.visual;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import java.lang.reflect.Field;
import java.util.*;

/** Only the private keypad guard; restores its preferences and performs no external actions. */
public final class UnlockSequenceTests extends Instrumentation {
    private Activity page;
    private int checks;
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private void ui(final Runnable action){runOnMainSync(action);waitForIdleSync();}
    private boolean locked(){return KeypadLockActivity.locked(getTargetContext());}
    private long armed()throws Exception{Field f=KeypadLockActivity.class.getDeclaredField("armed");f.setAccessible(true);return f.getLong(page);}
    private void open(){page=startActivitySync(new Intent(getTargetContext(),KeypadLockActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();check(locked(),"guard opened locked");}
    private void key(final int code,final boolean canceled){ui(new Runnable(){public void run(){long now=SystemClock.uptimeMillis();page.dispatchKeyEvent(new KeyEvent(now,now,0,code,0));page.dispatchKeyEvent(new KeyEvent(now,now+25,1,code,0,0,-1,0,canceled?KeyEvent.FLAG_CANCELED:0));}});}
    private void press(int code){key(code,false);}
    public void onCreate(Bundle args){start();}
    @SuppressWarnings("unchecked") public void onStart(){
        Bundle result=new Bundle();SharedPreferences p=getTargetContext().getSharedPreferences("keyguard",0);
        Map<String,?> saved=new HashMap<String,Object>(p.getAll());PowerManager.WakeLock awake=null;
        try{
            getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            awake=((PowerManager)getTargetContext().getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"keyshell:unlock-checks");awake.acquire(90000);
            open();press(KeyEvent.KEYCODE_STAR);check(locked(),"star alone keeps lock");
            key(KeyEvent.KEYCODE_DPAD_CENTER,true);check(armed()==0,"canceled OK does not arm");press(KeyEvent.KEYCODE_STAR);check(locked(),"star after canceled OK keeps lock");
            press(KeyEvent.KEYCODE_DPAD_CENTER);check(locked()&&armed()>0,"OK alone only arms first step");
            press(KeyEvent.KEYCODE_2);check(armed()==0,"wrong second key clears OK step");press(KeyEvent.KEYCODE_STAR);check(locked(),"wrong-key sequence cannot unlock");
            press(KeyEvent.KEYCODE_DPAD_CENTER);SystemClock.sleep(3200);waitForIdleSync();check(armed()==0,"OK step expires");press(KeyEvent.KEYCODE_STAR);check(locked(),"expired OK step cannot unlock");
            press(KeyEvent.KEYCODE_DPAD_CENTER);key(KeyEvent.KEYCODE_STAR,true);check(locked(),"canceled star keeps lock after OK");
            press(KeyEvent.KEYCODE_DPAD_CENTER);press(KeyEvent.KEYCODE_STAR);check(!locked(),"OK then completed star unlocks");page=null;
            open();press(KeyEvent.KEYCODE_ENTER);check(locked()&&armed()>0,"ENTER is an equivalent first step");press(KeyEvent.KEYCODE_STAR);check(!locked(),"ENTER then star unlocks");page=null;
            open();press(KeyEvent.KEYCODE_MENU);check(locked()&&armed()>0,"left softkey still arms");press(KeyEvent.KEYCODE_STAR);check(!locked(),"left softkey then star still unlocks");page=null;
            result.putString("result","PASS "+checks+" unlock sequence checks");
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{
            try{
                if(page!=null){final Activity old=page;ui(new Runnable(){public void run(){old.finish();}});}
                SharedPreferences.Editor e=p.edit().clear();
                for(Map.Entry<String,?> row:saved.entrySet()){String k=row.getKey();Object v=row.getValue();if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set)e.putStringSet(k,new HashSet<String>((Set<String>)v));}
                if(!e.commit())throw new IllegalStateException("guard preferences restore failed");
            }catch(Throwable error){result.putString("restore_error",android.util.Log.getStackTraceString(error));}
            if(awake!=null&&awake.isHeld())awake.release();
        }
        finish(result.containsKey("error")||result.containsKey("restore_error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}
