package ro.leo.visual;

import android.os.Handler;
import android.view.KeyEvent;

/** Defer Home digit/card actions until release; each hold fires once, even without repeats. */
final class HomeKeyPress {
    interface Actions {void shortPress(int key);void longPress(int key);}
    static final long HOLD_MS=650;
    private final Handler handler;
    private final Actions actions;
    private int key=-1;
    private long downTime;
    private boolean held;
    private final Runnable hold=new Runnable(){public void run(){
        if(key<0)return;
        held=true;
        actions.longPress(key);
    }};
    HomeKeyPress(Handler handler,Actions actions){this.handler=handler;this.actions=actions;}
    static boolean handles(int key){return key>=KeyEvent.KEYCODE_0&&key<=KeyEvent.KEYCODE_9||key==KeyEvent.KEYCODE_STAR||key==KeyEvent.KEYCODE_POUND;}
    void down(KeyEvent event){
        if(event.getRepeatCount()!=0)return;
        cancel();key=event.getKeyCode();downTime=event.getDownTime();
        handler.postDelayed(hold,HOLD_MS);
    }
    void up(KeyEvent event){
        boolean tap=key==event.getKeyCode()&&downTime==event.getDownTime()&&!held&&!event.isCanceled();
        int released=key;cancel();
        if(tap)actions.shortPress(released);
    }
    void cancel(){handler.removeCallbacks(hold);key=-1;held=false;}
}
