package ro.leo.visual;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;

/** Page-local keypad pointer. Nothing is scheduled while the pointer is idle. */
final class WebCursorView extends View {
    interface Listener { void changed(boolean enabled); }
    private final WebView web;
    private final Listener listener;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow=new Path();
    private final KeyRelease click=new KeyRelease(), toggle=new KeyRelease(), scrollToggle=new KeyRelease();
    private final float scale;
    private boolean enabled=true, positioned,scroll;
    private int speed=1;
    private float x,y,restoreX=.5f,restoreY=.5f,tapX,tapY;
    private int direction;
    private long since,tapDown;
    private final Runnable repeat=new Runnable(){public void run(){
        if(!enabled||direction==0||!hasWindowFocus()||!isShown()){stopMoving();return;}
        long held=SystemClock.uptimeMillis()-since;
        move(direction,step(held>=700?16:held>=350?10:6));
        postDelayed(this,40);
    }};
    private final Runnable afterTap=new Runnable(){public void run(){
        if(!isShown()||!hasWindowFocus())return;
        if(web.onCheckIsTextEditor())editorReady();
        // A SPA can replace/focus its input later. Never take focus back after a tap.
    }};
    private final Runnable releaseTap=new Runnable(){public void run(){
        if(tapDown==0)return;
        touch(MotionEvent.ACTION_UP);tapDown=0;postDelayed(afterTap,120);
    }};
    WebCursorView(Context context,WebView web,Listener listener){
        super(context);this.web=web;this.listener=listener;scale=getResources().getDisplayMetrics().density;
        setFocusable(true);setFocusableInTouchMode(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        arrow.moveTo(1,1);arrow.lineTo(3,19);arrow.lineTo(8,13);arrow.lineTo(14,21);
        arrow.lineTo(18,18);arrow.lineTo(12,10);arrow.lineTo(20,9);arrow.close();
    }
    boolean enabled(){return enabled;}
    boolean scrolling(){return scroll;}
    void speed(int value){speed=Math.max(0,Math.min(2,value));}
    private int step(int base){return Math.max(2,Math.round(base*scale*(speed==0?.5f:speed==2?1.6f:1f)));}
    void navigate(boolean previous){
        boolean first=enabled;if(enabled)setCursorEnabled(false);else suspend();
        WebFocus.move(web,previous,first);
    }
    float fractionX(){return getWidth()>1?x/(getWidth()-1):restoreX;}
    float fractionY(){return getHeight()>1?y/(getHeight()-1):restoreY;}
    void restore(boolean enabled,float x,float y){this.enabled=enabled;setFocusable(enabled);restoreX=Math.max(0,Math.min(1,x));restoreY=Math.max(0,Math.min(1,y));positioned=false;}
    void setCursorEnabled(boolean value){
        suspend();enabled=value;setFocusable(enabled);invalidate();
        if(enabled){requestFocus();((InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(getWindowToken(),0);}
        else web.requestFocus();
        listener.changed(enabled);
    }
    void editorReady(){
        if(!enabled||hasFocus()||!isShown()||!hasWindowFocus())return;
        suspend();enabled=false;setFocusable(false);invalidate();listener.changed(false);
        // The WebView's real or proxy editor already owns focus. Do not refocus it.
    }
    protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        float fx=positioned&&oldw>1?x/(oldw-1):restoreX,fy=positioned&&oldh>1?y/(oldh-1):restoreY;
        x=Math.max(0,Math.min(w-1,fx*(w-1)));y=Math.max(0,Math.min(h-1,fy*(h-1)));positioned=true;
    }
    protected void onDraw(Canvas canvas){
        if(!enabled)return;
        canvas.save();canvas.translate(x,y);
        canvas.scale(x>getWidth()-22*scale?-scale:scale,y>getHeight()-24*scale?-scale:scale);
        paint.setStyle(Paint.Style.FILL);paint.setColor(scroll?0xffffbd52:0xffdfbdff);canvas.drawPath(arrow,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);paint.setColor(0xff1a1028);canvas.drawPath(arrow,paint);
        canvas.restore();
    }
    boolean key(KeyEvent event){
        if(enabled&&!hasFocus()&&web.onCheckIsTextEditor())editorReady();
        int key=event.getKeyCode();
        // Keep # available to the keypad IME while editing an HTML field.
        if(key==KeyEvent.KEYCODE_POUND&&!web.onCheckIsTextEditor()){
            stopMoving();if(toggle.accept(event))setCursorEnabled(!enabled);return true;
        }
        if(!enabled)return false;
        if(key==KeyEvent.KEYCODE_STAR){stopMoving();if(scrollToggle.accept(event)){scroll=!scroll;invalidate();listener.changed(enabled);}return true;}
        boolean arrow=key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT;
        if(arrow){
            if(event.getAction()==KeyEvent.ACTION_UP){if(direction==key)stopMoving();}
            else if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){
                stopMoving();click.reset();direction=key;since=SystemClock.uptimeMillis();move(key,step(8));postDelayed(repeat,180);
            }
            return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){
            stopMoving();if(click.accept(event)){if(scroll){scroll=false;invalidate();listener.changed(enabled);}else tap();}return true;
        }
        return false;
    }
    private void move(int key,int step){
        int dx=key==KeyEvent.KEYCODE_DPAD_LEFT?-step:key==KeyEvent.KEYCODE_DPAD_RIGHT?step:0;
        int dy=key==KeyEvent.KEYCODE_DPAD_UP?-step:key==KeyEvent.KEYCODE_DPAD_DOWN?step:0;
        if(scroll){web.scrollBy(dx*5,dy*5);return;}
        x=Math.max(0,Math.min(getWidth()-1,x+dx));y=Math.max(0,Math.min(getHeight()-1,y+dy));
        int margin=Math.max(2,Math.round(10*scale));
        if(dy<0&&y<=margin||dy>0&&y>=getHeight()-1-margin)web.scrollBy(0,dy*3);
        if(dx<0&&x<=margin||dx>0&&x>=getWidth()-1-margin)web.scrollBy(dx*3,0);
        invalidate();
    }
    private void tap(){
        if(tapDown!=0)return;
        removeCallbacks(afterTap);tapX=x;tapY=y;tapDown=SystemClock.uptimeMillis();
        touch(MotionEvent.ACTION_DOWN);postDelayed(releaseTap,60);
    }
    private void touch(int action){
        MotionEvent event=MotionEvent.obtain(tapDown,SystemClock.uptimeMillis(),action,tapX,tapY,0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try{web.dispatchTouchEvent(event);}finally{event.recycle();}
    }
    private void stopMoving(){direction=0;removeCallbacks(repeat);}
    void suspend(){
        stopMoving();click.reset();toggle.reset();scrollToggle.reset();removeCallbacks(releaseTap);removeCallbacks(afterTap);
        if(tapDown!=0){touch(MotionEvent.ACTION_CANCEL);tapDown=0;}
    }
    protected void onDetachedFromWindow(){suspend();super.onDetachedFromWindow();}
}
