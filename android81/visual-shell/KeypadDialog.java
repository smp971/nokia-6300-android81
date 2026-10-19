package ro.leo.visual;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/** Shared keypad menu: explicit focus and softkey actions, no touch dependency. */
final class KeypadDialog extends Dialog {
    interface Action { void select(int index); }
    private final String title;
    private final String[] items;
    private final Action action;
    private int selected;
    private boolean centerPressed,backPressed;
    private View content;

    KeypadDialog(Context context, String title, String[] items, Action action) {
        super(context);
        this.title=title; this.items=items; this.action=action;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        content=new View(context) {
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            void text(Canvas c,String value,float x,float y,int size,int color,Paint.Align align) {
                paint.setTextSize(size); paint.setColor(color); paint.setTextAlign(align);
                c.drawText(value,x,y,paint);
            }
            protected void onDraw(Canvas c) {
                c.save(); c.scale(getWidth()/240f,getHeight()/280f);
                c.drawColor(0xff21172e);
                text(c,title,120,27,18,Color.WHITE,Paint.Align.CENTER);
                int first=Math.max(0,Math.min(Math.max(0,items.length-5),selected-2));
                for(int row=0;row<5 && first+row<items.length;row++) {
                    int index=first+row; float top=42+row*39;
                    if(index==selected) {
                        paint.setColor(0xfff5eefb);
                        c.drawRoundRect(new RectF(6,top,234,top+35),6,6,paint);
                    }
                    text(c,items[index],15,top+23,14,index==selected?0xff38224f:Color.WHITE,Paint.Align.LEFT);
                }
                paint.setColor(0xff100b19); c.drawRect(0,250,240,280,paint);
                text(c,"Alege",120,270,13,Color.WHITE,Paint.Align.CENTER);
                text(c,"Înapoi",233,270,12,Color.WHITE,Paint.Align.RIGHT);
                c.restore();
            }
        };
        setContentView(content);
    }
    public void show() {
        super.show();
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT);
    }
    public boolean onKeyDown(int key,KeyEvent event) {
        if(key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN) {
            selected=Math.max(0,Math.min(items.length-1,selected+(key==KeyEvent.KEYCODE_DPAD_UP?-1:1)));
            content.invalidate(); return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER) {
            if(event.getRepeatCount()==0) centerPressed=true;
            return true;
        }
        if(key==KeyEvent.KEYCODE_BACK) { if(event.getRepeatCount()==0) backPressed=true; return true; }
        if(key==KeyEvent.KEYCODE_MENU) return true;
        return super.onKeyDown(key,event);
    }
    public boolean onKeyUp(int key,KeyEvent event) {
        if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER) {
            boolean accept=centerPressed && !event.isCanceled(); centerPressed=false;
            if(accept) { dismiss(); action.select(selected); }
            return true;
        }
        if(key==KeyEvent.KEYCODE_BACK) {
            boolean close=backPressed && !event.isCanceled(); backPressed=false;
            if(close) dismiss(); return true;
        }
        if(key==KeyEvent.KEYCODE_MENU || key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN) return true;
        return super.onKeyUp(key,event);
    }
}
