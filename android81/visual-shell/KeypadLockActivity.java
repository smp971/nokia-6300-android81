package ro.leo.visual;
import android.content.*;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.Gravity;
import android.widget.*;

/** Accidental-key guard for Leo, not a PIN or Android security lock. */
public final class KeypadLockActivity extends LeoActivity {
    private long armed;
    static boolean visible;
    protected void onResume(){super.onResume();visible=true;}
    protected void onPause(){visible=false;super.onPause();}
    static boolean locked(Context c) { return c.getSharedPreferences("keyguard",0).getBoolean("locked",false); }
    static void unlock(Context c) { c.getSharedPreferences("keyguard",0).edit().putBoolean("locked",false).apply(); }
    protected void onCreate(Bundle state) {
        super.onCreate(state);getSharedPreferences("keyguard",0).edit().putBoolean("locked",true).apply();
        begin("Taste blocate"); TextView message=text("Apasă soft stânga,\napoi * în 3 secunde\npentru deblocare.\n\nBlocare pentru apăsări accidentale; fără PIN.",21);message.setGravity(Gravity.CENTER);root.addView(message,new LinearLayout.LayoutParams(-1,0,1));footer("Deblochează","","*");
    }
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key=event.getKeyCode();
        if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0) {
            if(key==KeyEvent.KEYCODE_MENU) { armed=SystemClock.elapsedRealtime();notice("Acum apasă *"); }
            else if(key==KeyEvent.KEYCODE_STAR && armed>0 && SystemClock.elapsedRealtime()-armed<=3000) { unlock(this);finish(); }
            else armed=0;
        }
        return true;
    }
    public void onBackPressed() { }
}
