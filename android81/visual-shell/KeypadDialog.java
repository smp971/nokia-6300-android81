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
    private final Appearance.Palette palette;

    KeypadDialog(Context context, String title, String[] items, Action action) {
        super(context);
        palette=Appearance.palette(context);
        this.title=title; this.items=items; this.action=action;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        content=new View(context) {
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            public boolean isOpaque(){return true;}
            void text(Canvas c,String value,float x,float y,int size,int color,Paint.Align align) {
                paint.setTextSize(size); paint.setColor(color); paint.setTextAlign(align);
                float max=align==Paint.Align.LEFT?218:220;
                if(paint.measureText(value)>max){int count=paint.breakText(value,true,max-paint.measureText("…"),null);value=value.substring(0,count)+"…";}
                c.drawText(value,x,y,paint);
            }
            protected void onDraw(Canvas c) {
                c.save(); c.scale(getWidth()/240f,getHeight()/280f);
                c.drawColor(palette.background);
                text(c,title,120,27,18,Color.WHITE,Paint.Align.CENTER);
                paint.setColor(palette.accent);c.drawRect(12,35,228,37,paint);
                int first=Math.max(0,Math.min(Math.max(0,items.length-5),selected-2));
                for(int row=0;row<5 && first+row<items.length;row++) {
                    int index=first+row; float top=42+row*39;
                    if(index==selected) {
                        paint.setColor(palette.selection);
                        c.drawRoundRect(new RectF(6,top,234,top+35),4,4,paint);
                    }
                    text(c,items[index],15,top+23,14,index==selected?palette.ink:Color.WHITE,Paint.Align.LEFT);
                }
                paint.setColor(palette.bar); c.drawRect(0,250,240,280,paint);
                paint.setColor(palette.accent);c.drawRect(0,250,240,251,paint);
                text(c,(items.length==0?0:selected+1)+" / "+items.length,12,245,10,palette.accent,Paint.Align.LEFT);
                text(c,items.length==0?"":"Alege",120,270,13,palette.accent,Paint.Align.CENTER);
                text(c,"Înapoi",233,270,12,Color.WHITE,Paint.Align.RIGHT);
                c.restore();
            }
        };
        setContentView(content);
    }
    public void show() {
        super.show();
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(palette.background));
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(palette.bar);
        WindowManager.LayoutParams attributes=getWindow().getAttributes();attributes.windowAnimations=0;getWindow().setAttributes(attributes);
        getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT);
    }
    public boolean dispatchKeyEvent(KeyEvent event) {
        if(LeoActivity.isJackNavigationNoise(event)) return true;
        return super.dispatchKeyEvent(event);
    }
    public boolean onKeyDown(int key,KeyEvent event) {
        if(key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN) {
            if(items.length>0)selected=CircularNavigation.step(selected,key==KeyEvent.KEYCODE_DPAD_UP?-1:1,items.length);
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
            if(accept&&items.length>0) { dismiss(); action.select(selected); }
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
