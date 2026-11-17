package ro.leo.visual;

import android.view.KeyEvent;

/** One discrete action per complete, uncanceled key press. */
final class KeyRelease {
    private int key = -1;
    private long down;
    boolean accept(KeyEvent event) {
        if(event.getAction()==KeyEvent.ACTION_DOWN) {
            if(event.getRepeatCount()==0) { key=event.getKeyCode(); down=event.getDownTime(); }
            return false;
        }
        if(event.getAction()!=KeyEvent.ACTION_UP || event.getKeyCode()!=key)return false;
        boolean accepted=event.getDownTime()==down && !event.isCanceled();
        reset();
        return accepted;
    }
    void reset(){key=-1;down=0;}
}
