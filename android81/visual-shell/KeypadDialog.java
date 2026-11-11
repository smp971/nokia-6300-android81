package ro.leo.visual;
import ro.leo.i18n.L;

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
    private static java.lang.ref.WeakReference<KeypadDialog> active=new java.lang.ref.WeakReference<KeypadDialog>(null);
    static KeypadDialog current(){KeypadDialog d=active.get();return d!=null&&d.isShowing()?d:null;}
    private final String title;
    private final String[] items;
    private final Action action;
    private int selected;
    private boolean centerPressed,backPressed;
    private final boolean numbered;
    private int digitPressed=-1;
    private Runnable backAction;
    private View content;
    private final Appearance.Palette palette;

    KeypadDialog(Context context, String title, String[] items, Action action) {
        this(context,title,items,action,false);
    }
    KeypadDialog(Context context, String title, String[] items, Action action, boolean numbered) {
        this(context,title,items,action,numbered,Appearance.palette(context));
    }
    KeypadDialog(Context context, String title, String[] items, Action action, boolean numbered,
                 Appearance.Palette palette) {
        super(context);
        this.numbered=numbered;
        this.palette=palette;
        this.title=title; this.items=items; this.action=action;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        content=new View(context) {
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            public boolean isOpaque(){return true;}
            void text(Canvas c,String value,float x,float y,int size,int color,Paint.Align align) {
                paint.setTextSize(size*Math.min(1.3f,Appearance.textScale(getContext()))); paint.setColor(color); paint.setTextAlign(align);
                float max=align==Paint.Align.LEFT?233-x:220;
                if(paint.measureText(value)>max){int count=paint.breakText(value,true,max-paint.measureText("…"),null);value=value.substring(0,count)+"…";}
                c.drawText(value,x,y,paint);
            }
            protected void onDraw(Canvas c) {
                c.save(); c.scale(getWidth()/240f,getHeight()/280f);
                c.drawColor(palette.background);
                if(palette.material())text(c,title,15,27,18,Color.WHITE,Paint.Align.LEFT);else {text(c,title,120,27,18,Color.WHITE,Paint.Align.CENTER);paint.setColor(palette.accent);c.drawRect(12,35,228,37,paint);}
                int visible=items.length<=7?Math.max(1,items.length):6;
                float height=Math.min(39,196f/visible);
                int first=Math.max(0,Math.min(Math.max(0,items.length-visible),selected-visible/2));
                for(int row=0;row<visible && first+row<items.length;row++) {
                    int index=first+row; float top=42+row*height;
                    if(index==selected) {
                        paint.setColor(palette.selection);
                        if(palette.sxmo())c.drawRect(6,top,234,top+height-2,paint);
                        else {float radius=palette.material()?12:4;c.drawRoundRect(new RectF(6,top,234,top+height-2),radius,radius,paint);if(palette.material()){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(palette.accent);c.drawRoundRect(new RectF(6,top,234,top+height-2),radius,radius,paint);paint.setStyle(Paint.Style.FILL);}}
                    }
                    float baseline=top+height/2+5;
                    if(numbered)text(c,index<9?""+(index+1):"",15,baseline,11,index==selected?palette.ink:palette.accent,Paint.Align.LEFT);
                    text(c,items[index],numbered?35:15,baseline,14,index==selected?palette.ink:Color.WHITE,Paint.Align.LEFT);
                }
                paint.setColor(palette.bar); c.drawRect(0,250,240,280,paint);
                paint.setColor(palette.material()?0xff49454f:palette.accent);c.drawRect(0,250,240,251,paint);
                text(c,(items.length==0?0:selected+1)+" / "+items.length,12,245,10,palette.accent,Paint.Align.LEFT);
                if(numbered)text(c,"1–"+Math.min(9,items.length),8,270,11,Color.WHITE,Paint.Align.LEFT);
                text(c,items.length==0?"":L.t("Alege"),120,270,13,palette.accent,Paint.Align.CENTER);
                text(c,L.t("Înapoi"),233,270,12,Color.WHITE,Paint.Align.RIGHT);
                c.restore();
            }
        };
        setContentView(content);
    }
    KeypadDialog onBack(Runnable action){backAction=action;return this;}
    KeypadDialog initialSelection(int index) {
        selected = Math.max(0, Math.min(index, items.length - 1));
        return this;
    }
    public void show() {
        super.show();active=new java.lang.ref.WeakReference<KeypadDialog>(this);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(palette.background));
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(palette.bar);getWindow().setNavigationBarColor(palette.bar);getWindow().getDecorView().setSystemUiVisibility(SystemChrome.darkFlags(getWindow().getDecorView().getSystemUiVisibility()));
        WindowManager.LayoutParams attributes=getWindow().getAttributes();attributes.windowAnimations=0;getWindow().setAttributes(attributes);
        getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT);
    }
    public boolean dispatchKeyEvent(KeyEvent event) {
        if(LeoActivity.isJackNavigationNoise(event)) return true;
        return super.dispatchKeyEvent(event);
    }
    public boolean onKeyDown(int key,KeyEvent event) {
        if(numbered&&key>=KeyEvent.KEYCODE_1&&key<=KeyEvent.KEYCODE_9){
            int at=key-KeyEvent.KEYCODE_1;
            if(event.getRepeatCount()==0&&at<items.length){digitPressed=key;selected=at;content.invalidate();}
            return true;
        }
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
        if(numbered&&key>=KeyEvent.KEYCODE_1&&key<=KeyEvent.KEYCODE_9){
            boolean accept=digitPressed==key&&!event.isCanceled();digitPressed=-1;
            if(accept){dismiss();action.select(key-KeyEvent.KEYCODE_1);}return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER) {
            boolean accept=centerPressed && !event.isCanceled(); centerPressed=false;
            if(accept&&items.length>0) { dismiss(); action.select(selected); }
            return true;
        }
        if(key==KeyEvent.KEYCODE_BACK) {
            boolean close=backPressed && !event.isCanceled(); backPressed=false;
            if(close){dismiss();if(backAction!=null)backAction.run();}return true;
        }
        if(key==KeyEvent.KEYCODE_MENU || key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN) return true;
        return super.onKeyUp(key,event);
    }
}
