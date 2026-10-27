package ro.leo.visual;
import ro.leo.i18n.L;
import android.content.Context;
import android.view.KeyEvent;

/** Pace held arrows in Leo. Fresh presses, releases, text and call keys pass through. */
final class NavigationPace {
    static final String[] LABELS={"Sistem · normal","Mai lent","Pas cu pas"};
    private long last;private int held;
    static String label(int mode){return L.t(LABELS[Math.max(0,Math.min(2,mode))]);}
    static boolean reject(int mode,int repeats,long heldMs,long gapMs){
        return repeats>0&&(mode==2||(mode==1&&(heldMs<500||gapMs<180)));
    }
    boolean consume(Context c,KeyEvent event){
        int key=event.getKeyCode();
        if(key<KeyEvent.KEYCODE_DPAD_UP||key>KeyEvent.KEYCODE_DPAD_RIGHT)return false;
        if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
        long now=event.getEventTime();
        if(event.getRepeatCount()==0||key!=held){held=key;last=now;return false;}
        int mode=c.getSharedPreferences("accessibility",0).getInt("repeat",0);
        if(reject(mode,event.getRepeatCount(),now-event.getDownTime(),now-last))return true;
        last=now;return false;
    }
}
