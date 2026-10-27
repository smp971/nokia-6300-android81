package ro.leo.visual;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import java.lang.reflect.*;
/** Navigation only. Never presses CALL or selects a call-log entry. */
public final class PhoneTabsTests extends Instrumentation {
    PhoneActivity page;int checks;
    Object get(String name)throws Exception{Field f=PhoneActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(page);}
    void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    void key(final int code){runOnMainSync(new Runnable(){public void run(){page.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));page.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));}});waitForIdleSync();}
    void ready(int expected)throws Exception{for(int i=0;i<100;i++){waitForIdleSync();LinearLayout bar=(LinearLayout)get("tabBar");if((Integer)get("page")==expected&&bar.getParent()==page.root&&bar.getChildAt(expected).hasFocus())return;SystemClock.sleep(100);}throw new AssertionError("Tab did not receive focus: "+expected);}
    public void onCreate(Bundle args){start();}
    public void onStart(){Bundle out=new Bundle();try{
        page=(PhoneActivity)startActivitySync(new Intent(getTargetContext(),PhoneActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        final EditText number=(EditText)get("number");runOnMainSync(new Runnable(){public void run(){page.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);number.setText("123");number.setSelection(2);number.requestFocus();}});
        key(KeyEvent.KEYCODE_DPAD_LEFT);check((Integer)get("page")==0&&number.getSelectionStart()==1,"left edits number instead of changing tab");
        setInTouchMode(true);
        runOnMainSync(new Runnable(){public void run(){number.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP));}});
        ready(0);check(true,"tabs reachable from number in touch mode");
        key(KeyEvent.KEYCODE_DPAD_RIGHT);ready(1);check(true,"dial to history");
        key(KeyEvent.KEYCODE_DPAD_RIGHT);ready(2);check(true,"history to statistics");
        key(KeyEvent.KEYCODE_DPAD_DOWN);for(int n=0;n<100&&!(page.getCurrentFocus() instanceof ListView);n++){SystemClock.sleep(100);waitForIdleSync();}check(page.getCurrentFocus() instanceof ListView,"down enters statistics list, including while loading");
        key(KeyEvent.KEYCODE_DPAD_RIGHT);ready(0);check(true,"statistics list wraps to dial tab");
        check("123".equals(((EditText)get("number")).getText().toString()),"draft preserved");
        key(KeyEvent.KEYCODE_DPAD_LEFT);ready(2);check(true,"backward wrap to statistics");
        key(KeyEvent.KEYCODE_DPAD_RIGHT);ready(0);key(KeyEvent.KEYCODE_DPAD_DOWN);check(((EditText)get("number")).hasFocus(),"down returns to number");
        final LinearLayout favorites=(LinearLayout)get("favoriteRow");runOnMainSync(new Runnable(){public void run(){favorites.getChildAt(0).requestFocus();}});key(KeyEvent.KEYCODE_DPAD_RIGHT);
        check((Integer)get("page")==0,"favorite-card navigation retains dial page");
        out.putString("result","PASS "+checks+" phone-tab navigation checks; no calls placed");
    }catch(Throwable e){out.putString("error",android.util.Log.getStackTraceString(e));}finally{if(page!=null)runOnMainSync(new Runnable(){public void run(){page.finish();}});setInTouchMode(false);}
    finish(out.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,out);}
}
