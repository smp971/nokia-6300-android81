package ro.leo.visual;

import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import java.lang.reflect.Method;

/** Opt-in, bounded control-key trace. No text, digits, idle polling or wake lock. */
final class KeyDiagnostics {
    private static Method property;
    private static long nextRead,until,session;
    private static int count;
    static String control(int key){
        switch(key){
            case KeyEvent.KEYCODE_DPAD_UP:return "up";
            case KeyEvent.KEYCODE_DPAD_DOWN:return "down";
            case KeyEvent.KEYCODE_DPAD_LEFT:return "left";
            case KeyEvent.KEYCODE_DPAD_RIGHT:return "right";
            case KeyEvent.KEYCODE_BACK:return "back";
            case KeyEvent.KEYCODE_CALL:return "call";
            default:return null;
        }
    }
    static synchronized void record(String route,KeyEvent event){
        String key=control(event.getKeyCode());if(key==null)return;
        long now=SystemClock.elapsedRealtime();
        if(now>=nextRead){
            nextRead=now+1000;
            try{
                if(property==null)property=Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class);
                long value=Long.parseLong((String)property.invoke(null,"sys.leo.keytrace.until","0"));
                if(value!=session){session=value;count=0;}
                until=value;
            }catch(Exception unavailable){until=0;}
        }
        if(now>=until||until-now>1200000||count>=400)return;
        count++;
        Log.i("KeyShellInput",route+" control="+key+" action="+event.getAction()
            +" repeat="+event.getRepeatCount()+" scan="+event.getScanCode()+" device="+event.getDeviceId()
            +" down_ms="+event.getDownTime()+" event_ms="+event.getEventTime()+" seen_boot_ms="+now
            +" canceled="+event.isCanceled());
    }
}
