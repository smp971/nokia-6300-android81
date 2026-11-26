package ro.leo.visual;

import android.os.SystemClock;
import android.view.KeyEvent;
import java.lang.reflect.*;

/** Root-side keypad injector; only the three fixed physical soft/menu combinations. */
public final class CompanionInputBridge {
    public static void main(String[] args)throws Exception {
        if(android.os.Process.myUid()!=0||args.length!=2)throw new SecurityException();
        int code=Integer.parseInt(args[0]),scan=Integer.parseInt(args[1]);
        if(!((code==82&&(scan==139||scan==212))||(code==4&&scan==158)))throw new SecurityException();
        Class<?> type=Class.forName("android.hardware.input.InputManager");Object manager=type.getMethod("getInstance").invoke(null);
        Method inject=type.getMethod("injectInputEvent",android.view.InputEvent.class,int.class);
        long time=SystemClock.uptimeMillis();
        for(int action:new int[]{KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP}){
            KeyEvent key=new KeyEvent(time,SystemClock.uptimeMillis(),action,code,0,0,-1,scan,KeyEvent.FLAG_FROM_SYSTEM,android.view.InputDevice.SOURCE_KEYBOARD);
            if(!Boolean.TRUE.equals(inject.invoke(manager,key,2)))throw new IllegalStateException("Input rejected");
            if(action==KeyEvent.ACTION_DOWN)SystemClock.sleep(35);
        }
    }
}
