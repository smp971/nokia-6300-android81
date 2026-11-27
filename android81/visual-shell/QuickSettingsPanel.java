package ro.leo.visual;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.view.accessibility.*;
import ro.leo.i18n.L;

/** Five live tiles and two keypad sliders. One small Canvas, no animation or polling. */
final class QuickSettingsPanel extends View {
    interface Actions {void activate(int item);void value(int item,int value);void focused(int item);}
    static final int BRIGHTNESS=5,MEDIA=6;
    final RectF[] bounds=new RectF[7];
    private final android.text.TextPaint paint=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Actions actions;
    private final int[] location=new int[2];
    private final Rect visible=new Rect();
    private Appearance.Palette palette;
    private QuickSettingsControls.State state;
    private SettingsIcon[][] icons;
    private float scale,density;
    private int selected,lastTile=2,touch=-1;
    private long okDown=-1;
    QuickSettingsPanel(Context c,Actions actions,int selected){super(c);this.actions=actions;this.selected=Math.max(0,Math.min(6,selected));if(this.selected>=2&&this.selected<5)lastTile=this.selected;for(int i=0;i<7;i++)bounds[i]=new RectF();setFocusable(true);setFocusableInTouchMode(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);style(Appearance.palette(c),Appearance.textScale(c));}
    private float dp(float value){return value*density;}
    void style(Appearance.Palette colors,float textScale){palette=colors;scale=textScale;// The reference QVGA device reports ldpi; retain readable 13px labels there.
        density=Math.max(1f,getResources().getDisplayMetrics().density);rebuildIcons();requestLayout();invalidate();}
    void state(QuickSettingsControls.State next){boolean profileChanged=state==null||next.mutedProfile!=state.mutedProfile;state=next;if(profileChanged)rebuildIcons();invalidate();}
    QuickSettingsControls.State state(){return state;}
    int selected(){return selected;}
    private void rebuildIcons(){
        SettingsIcon.Glyph[] glyphs={SettingsIcon.Glyph.WIFI,SettingsIcon.Glyph.BLUETOOTH,SettingsIcon.Glyph.LOCATION,SettingsIcon.Glyph.TETHERING,state!=null&&state.mutedProfile?SettingsIcon.Glyph.MUTE:SettingsIcon.Glyph.SOUND,SettingsIcon.Glyph.BRIGHTNESS,SettingsIcon.Glyph.SOUND};
        icons=new SettingsIcon[2][7];for(int active=0;active<2;active++)for(int i=0;i<7;i++)icons[active][i]=new SettingsIcon(getContext(),glyphs[i],Math.round(dp((i<5?22:18)*scale)),ColorStateList.valueOf(active==1?palette.ink:palette.accent));
    }
    private float layout(int width){
        float edge=dp(6),gap=dp(5),tile=dp(58*scale),slider=dp(44*scale),available=width-2*edge;
        float two=(available-gap)/2,three=(available-2*gap)/3,y=edge;
        bounds[0].set(edge,y,edge+two,y+tile);bounds[1].set(edge+two+gap,y,width-edge,y+tile);y+=tile+gap;
        for(int i=2;i<5;i++)bounds[i].set(edge+(i-2)*(three+gap),y,edge+(i-2)*(three+gap)+three,y+tile);
        y+=tile+dp(8);bounds[5].set(edge,y,width-edge,y+slider);y+=slider+gap;bounds[6].set(edge,y,width-edge,y+slider);return y+slider+edge;
    }
    protected void onMeasure(int width,int height){int w=resolveSize(Math.round(dp(240)),width);int h=Math.round(layout(w));setMeasuredDimension(w,resolveSize(h,height));}
    protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(state==null)return;
        for(int i=0;i<5;i++)tile(canvas,i);slider(canvas,BRIGHTNESS,state.brightnessPercent(),5,100,state.brightnessPercent()+"%",state.writable);slider(canvas,MEDIA,state.media,0,state.mediaMax,state.media+" / "+state.mediaMax,state.mediaMax>0);
    }
    private void surface(Canvas canvas,RectF box,boolean active,int item){
        paint.setStyle(Paint.Style.FILL);paint.setColor(active?palette.selection:palette.card);canvas.drawRoundRect(box,dp(11),dp(11),paint);
        if(hasFocus()&&selected==item){paint.setStyle(Paint.Style.STROKE);paint.setColor(palette.accent);paint.setStrokeWidth(dp(2));canvas.drawRoundRect(box.left+dp(1),box.top+dp(1),box.right-dp(1),box.bottom-dp(1),dp(10),dp(10),paint);paint.setStyle(Paint.Style.FILL);}
    }
    private void label(Canvas canvas,String value,float x,float baseline,float size,int color,Paint.Align align,float width){
        paint.setColor(color);paint.setTextSize(dp(size*scale));paint.setTypeface(Typeface.DEFAULT);paint.setTextAlign(align);
        String fitted=TextUtils.ellipsize(value,paint,Math.max(1,width),TextUtils.TruncateAt.END).toString();canvas.drawText(fitted,x,baseline,paint);
    }
    private void icon(Canvas canvas,int item,boolean active,float x,float y){Drawable drawable=icons[active?1:0][item];canvas.save();canvas.translate(x-drawable.getBounds().width()/2f,y-drawable.getBounds().height()/2f);drawable.draw(canvas);canvas.restore();}
    private void tile(Canvas canvas,int item){
        RectF box=bounds[item];boolean active=state.toggles[item]==QuickSettingsControls.ON;surface(canvas,box,active,item);
        icon(canvas,item,active,box.centerX(),box.top+dp(17*scale));
        int ink=active?palette.ink:Color.WHITE,secondary=active?Appearance.mix(palette.selection,palette.ink,.82f):palette.secondary();
        label(canvas,QuickSettingsControls.TITLES[item],box.centerX(),box.top+dp(43*scale),13,ink,Paint.Align.CENTER,box.width()-dp(12));
        label(canvas,state.labels[item],box.centerX(),box.top+dp(55*scale),10,secondary,Paint.Align.CENTER,box.width()-dp(12));
        if(item<4){paint.setColor(state.toggles[item]==QuickSettingsControls.BUSY?0xfff2cc80:active?0xff91dcb6:palette.divider());canvas.drawCircle(box.right-dp(9),box.top+dp(9),dp(2.4f),paint);}
    }
    private void slider(Canvas canvas,int item,int value,int min,int max,String text,boolean available){
        RectF box=bounds[item];surface(canvas,box,false,item);int color=available?palette.accent:palette.secondary();
        label(canvas,QuickSettingsControls.TITLES[item],box.left+dp(10),box.top+dp(15*scale),13,Color.WHITE,Paint.Align.LEFT,box.width()-dp(78*scale));
        label(canvas,available?text:L.t("Lipsă"),box.right-dp(10),box.top+dp(15*scale),12,color,Paint.Align.RIGHT,dp(68*scale));
        float y=box.top+dp(31*scale),left=box.left+dp(34*scale),right=box.right-dp(12),radius=dp(4),progress=max<=min?0:Math.max(0,Math.min(1,(value-min)/(float)(max-min)));
        icon(canvas,item,false,box.left+dp(18*scale),y);paint.setColor(palette.divider());canvas.drawRoundRect(left,y-radius,right,y+radius,radius,radius,paint);
        float thumb=left+(right-left)*progress;paint.setColor(color);if(thumb>left)canvas.drawRoundRect(left,y-radius,thumb,y+radius,radius,radius,paint);canvas.drawCircle(thumb,y,dp(selected==item&&hasFocus()?6:5),paint);
    }
    static int navigation(int item,int key,int previousTile){
        if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){int direction=key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1;if(item<2)return 1-item;if(item<5)return 2+Math.floorMod(item-2+direction,3);return item;}
        if(key==KeyEvent.KEYCODE_DPAD_UP){if(item<2)return MEDIA;if(item<5)return item==4?1:0;return item==BRIGHTNESS?Math.max(2,Math.min(4,previousTile)):BRIGHTNESS;}
        if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(item<2)return item==0?2:4;if(item<5)return BRIGHTNESS;return item==BRIGHTNESS?MEDIA:0;}return item;
    }
    void select(int item){selected=Math.max(0,Math.min(6,item));if(selected>=2&&selected<5)lastTile=selected;okDown=-1;actions.focused(selected);invalidate();ensureVisible();sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);}
    private void ensureVisible(){post(new Runnable(){public void run(){Rect box=new Rect();bounds[selected].roundOut(box);box.inset(0,-Math.round(dp(3)));requestRectangleOnScreen(box,true);}});}
    protected void onFocusChanged(boolean focused,int direction,Rect previous){super.onFocusChanged(focused,direction,previous);okDown=-1;invalidate();if(focused)ensureVisible();}
    public boolean onKeyDown(int key,KeyEvent event){
        if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){if(event.getRepeatCount()==0)okDown=event.getDownTime();return true;}
        if(key>=KeyEvent.KEYCODE_DPAD_UP&&key<=KeyEvent.KEYCODE_DPAD_RIGHT){
            if(selected>=5&&(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)){if(state!=null)change(selected,(selected==BRIGHTNESS?state.brightnessPercent():state.media)+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1)*(selected==BRIGHTNESS?5:1));}
            else select(navigation(selected,key,lastTile));return true;
        }return super.onKeyDown(key,event);
    }
    public boolean onKeyUp(int key,KeyEvent event){if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){boolean activate=okDown==event.getDownTime()&&!event.isCanceled();okDown=-1;if(activate)actions.activate(selected);return true;}return super.onKeyUp(key,event);}
    private void change(int item,int value){if(state==null)return;if(item==BRIGHTNESS&&!state.writable||item==MEDIA&&state.mediaMax<=0)return;actions.value(item,QuickSettingsControls.bounded(value,item==BRIGHTNESS?5:0,item==BRIGHTNESS?100:state.mediaMax));}
    public boolean onTouchEvent(MotionEvent event){
        if(event.getAction()==MotionEvent.ACTION_DOWN){touch=-1;for(int i=0;i<7;i++)if(bounds[i].contains(event.getX(),event.getY()))touch=i;if(touch<0)return false;requestFocus();select(touch);return true;}
        if(event.getAction()==MotionEvent.ACTION_UP&&touch>=0){int item=touch;touch=-1;if(!bounds[item].contains(event.getX(),event.getY()))return true;if(item<5)actions.activate(item);else{RectF box=bounds[item];float start=box.left+dp(34*scale),end=box.right-dp(12),fraction=Math.max(0,Math.min(1,(event.getX()-start)/Math.max(1,end-start)));int min=item==BRIGHTNESS?5:0,max=item==BRIGHTNESS?100:state.mediaMax;change(item,min+Math.round(fraction*(max-min)));}performClick();return true;}
        if(event.getAction()==MotionEvent.ACTION_CANCEL)touch=-1;return true;
    }
    public boolean performClick(){super.performClick();return true;}
    private String description(int item){if(state==null)return QuickSettingsControls.TITLES[item];return QuickSettingsControls.TITLES[item]+" · "+(item<5?state.labels[item]:item==BRIGHTNESS?state.brightnessPercent()+"%":state.media+" / "+state.mediaMax);}
    private final AccessibilityNodeProvider accessibility=new AccessibilityNodeProvider(){
        public AccessibilityNodeInfo createAccessibilityNodeInfo(int id){
            if(id==View.NO_ID){AccessibilityNodeInfo host=AccessibilityNodeInfo.obtain(QuickSettingsPanel.this);onInitializeAccessibilityNodeInfo(host);for(int i=0;i<7;i++)host.addChild(QuickSettingsPanel.this,i);return host;}
            if(id<0||id>=7||state==null)return null;AccessibilityNodeInfo info=AccessibilityNodeInfo.obtain();info.setSource(QuickSettingsPanel.this,id);info.setParent(QuickSettingsPanel.this);info.setPackageName(getContext().getPackageName());info.setClassName(id>=5?"android.widget.SeekBar":id==4?"android.widget.Button":"android.widget.Switch");info.setContentDescription(description(id));info.setEnabled(id==BRIGHTNESS?state.writable:id==MEDIA?state.mediaMax>0:true);info.setFocusable(true);info.setFocused(selected==id);info.addAction(AccessibilityNodeInfo.ACTION_FOCUS);
            if(id<4){info.setCheckable(true);info.setChecked(state.toggles[id]==QuickSettingsControls.ON);}if(id<5)info.addAction(AccessibilityNodeInfo.ACTION_CLICK);else{info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,id==5?5:0,id==5?100:state.mediaMax,id==5?Math.max(5,state.brightnessPercent()):state.media));info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);}
            Rect rect=new Rect();bounds[id].roundOut(rect);info.setBoundsInParent(rect);getLocationOnScreen(location);rect.offset(location[0],location[1]);info.setBoundsInScreen(rect);getGlobalVisibleRect(visible);info.setVisibleToUser(isShown()&&Rect.intersects(rect,visible));return info;
        }
        public boolean performAction(int id,int action,Bundle args){if(id<0||id>=7||state==null)return false;requestFocus();select(id);if(action==AccessibilityNodeInfo.ACTION_FOCUS)return true;if(action==AccessibilityNodeInfo.ACTION_CLICK&&id<5){actions.activate(id);return true;}if(id>=5){int current=id==5?state.brightnessPercent():state.media,step=id==5?5:1;if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)change(id,current+step);else if(action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)change(id,current-step);else if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()&&args!=null)change(id,Math.round(args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,current)));else return false;return true;}return false;}
    };
    public AccessibilityNodeProvider getAccessibilityNodeProvider(){return accessibility;}
}
