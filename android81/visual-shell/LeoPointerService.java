package ro.leo.visual;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.*;
import android.util.Log;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

/** Opt-in global keypad pointer. No window contents, screenshots or network access. */
public final class LeoPointerService extends AccessibilityService {
    private static LeoPointerService instance;
    private WindowManager manager;
    private WindowManager.LayoutParams layout;
    private View pointer;
    private boolean active, scroll, gesturePending, consumePoundUp;
    private int x, y, width, height;
    private KeypadDialog menu;
    private boolean screenWatching;
    private final android.content.BroadcastReceiver screenReceiver=new android.content.BroadcastReceiver(){
        public void onReceive(android.content.Context c,android.content.Intent intent){
            boolean busy=AlarmRingService.ringing;
            try{busy|=((android.telecom.TelecomManager)getSystemService(TELECOM_SERVICE)).isInCall();}catch(RuntimeException ignored){}
            if(android.content.Intent.ACTION_SCREEN_OFF.equals(intent.getAction())){
                setActive(false);if(menu!=null)menu.dismiss();
                if(!busy && getSharedPreferences("keyguard",0).getBoolean("screen_off",false))
                    getSharedPreferences("keyguard",0).edit().putBoolean("locked",true).apply();
            }else if(!busy && KeypadLockActivity.locked(LeoPointerService.this) && !KeypadLockActivity.visible){
                try{startActivity(new android.content.Intent(LeoPointerService.this,KeypadLockActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP));}
                catch(RuntimeException unavailable){Log.w("LeoPointer","Lock window unavailable",unavailable);}
            }
        }
    };
    private final android.os.Handler movement=new android.os.Handler();
    private int heldDirection;
    private long heldSince;
    private final Runnable repeatMovement=new Runnable() {
        public void run() {
            if(!active || scroll || heldDirection==0) return;
            long elapsed=android.os.SystemClock.uptimeMillis()-heldSince;
            movePointer(heldDirection,elapsed>=700?14:elapsed>=350?8:4);
            movement.postDelayed(this,40);
        }
    };
    private void stopMovement() {
        heldDirection=0; movement.removeCallbacks(repeatMovement);
    }
    private void movePointer(int key,int step) {
        if(key==KeyEvent.KEYCODE_DPAD_LEFT) x-=step;
        if(key==KeyEvent.KEYCODE_DPAD_RIGHT) x+=step;
        if(key==KeyEvent.KEYCODE_DPAD_UP) y-=step;
        if(key==KeyEvent.KEYCODE_DPAD_DOWN) y+=step;
        x=Math.max(0,Math.min(width-2,x)); y=Math.max(0,Math.min(height-2,y));
        layout.x=x; layout.y=y; manager.updateViewLayout(pointer,layout);
        Log.i("LeoPointer","move x="+x+" y="+y);
    }
    protected void onServiceConnected() {
        instance=this;
        manager=(WindowManager)getSystemService(WINDOW_SERVICE);
        if(!screenWatching){android.content.IntentFilter f=new android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF);f.addAction(android.content.Intent.ACTION_SCREEN_ON);registerReceiver(screenReceiver,f);screenWatching=true;}
    }
    public static boolean toggle() {
        if(instance==null) return false;
        instance.setActive(!instance.active); return true;
    }
    static void prepareForAlarm() {
        if(instance==null) return;
        instance.setActive(false);
        if(instance.menu!=null) instance.menu.dismiss();
    }
    static boolean startCenteredTest() {
        if(instance==null) return false;
        instance.setActive(false); instance.setActive(true); return instance.active;
    }
    private void globalMenu() {
        stopMovement();
        if(menu!=null && menu.isShowing()) { menu.dismiss(); return; }
        menu=new KeypadDialog(new ContextThemeWrapper(this,android.R.style.Theme_Material_NoActionBar),
            "Meniu Leo",new String[]{"Notificări",active?"Oprește mouse-ul":"Mouse din taste","Acasă","Setări Leo","Blochează tastele","Închide"},new KeypadDialog.Action() {
                public void select(int which) {
                    if(which==0) performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
                    else if(which==1) setActive(!active);
                    else if(which==2) performGlobalAction(GLOBAL_ACTION_HOME);
                    else if(which==3) startActivity(new android.content.Intent(LeoPointerService.this,SettingsActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                    else if(which==4) {setActive(false);startActivity(new android.content.Intent(LeoPointerService.this,KeypadLockActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));}
                }
            });
        menu.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        menu.show();
        Log.i("LeoPointer","global menu opened");
    }
    private void setActive(boolean enable) {
        stopMovement();
        if(enable==active) return;
        if(enable) {
            android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();
            manager.getDefaultDisplay().getRealMetrics(metrics);
            width=metrics.widthPixels; height=metrics.heightPixels; x=width/2; y=height/2;
            scroll=false;
            pointer=new View(this) {
                private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
                private final Path arrow=new Path();
                protected void onDraw(Canvas c) {
                    arrow.reset(); arrow.moveTo(1,1); arrow.lineTo(3,19); arrow.lineTo(8,13); arrow.lineTo(14,21); arrow.lineTo(18,18); arrow.lineTo(12,10); arrow.lineTo(20,9); arrow.close();
                    paint.setColor(scroll?0xffffbd52:0xffdfbdff); paint.setStyle(Paint.Style.FILL); c.drawPath(arrow,paint);
                    paint.setColor(0xff1a1028); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f); c.drawPath(arrow,paint);
                }
            };
            layout=new WindowManager.LayoutParams(23,24,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
            layout.gravity=Gravity.TOP|Gravity.LEFT; layout.x=x; layout.y=y;
            try { manager.addView(pointer,layout); active=true; }
            catch(RuntimeException error) { pointer=null; return; }
            Toast.makeText(this,"Mouse: săgeți · OK clic · # oprire · * derulare",Toast.LENGTH_LONG).show();
        } else {
            active=false;
            if(pointer!=null) { try { manager.removeView(pointer); } catch(RuntimeException ignored) { } pointer=null; }
        }
    }
    protected boolean onKeyEvent(KeyEvent event) {
        int key=event.getKeyCode();
        boolean guard=KeypadLockActivity.locked(this);
        boolean call=false;
        if(guard) try {call=((android.telecom.TelecomManager)getSystemService(TELECOM_SERVICE)).isInCall();}catch(RuntimeException ignored){}
        if(guard && !AlarmRingService.ringing && !call) {
            if(active)setActive(false);
            if(KeypadLockActivity.visible) return key==KeyEvent.KEYCODE_HOME || key==KeyEvent.KEYCODE_ENDCALL || key==KeyEvent.KEYCODE_APP_SWITCH || key==KeyEvent.KEYCODE_NOTIFICATION;
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) startActivity(new android.content.Intent(this,KeypadLockActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP));
            return true;
        }
        if(event.getAction()==KeyEvent.ACTION_UP && key==heldDirection) stopMovement();
        // Scan 212 is the dedicated Menu key; scan 139 is the left softkey.
        // Keep the softkey's context-specific actions intact.
        if(key==KeyEvent.KEYCODE_MENU && event.getScanCode()==212) {
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) globalMenu();
            return true;
        }
        if(menu!=null && menu.isShowing()) return false;
        if(key==KeyEvent.KEYCODE_POUND && event.getAction()==KeyEvent.ACTION_UP && consumePoundUp) { consumePoundUp=false; return true; }
        if(!active) return false;
        boolean handled=key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN
            || key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT
            || key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER
            || key==KeyEvent.KEYCODE_POUND || key==KeyEvent.KEYCODE_STAR;
        if(!handled) return false;
        if(event.getAction()!=KeyEvent.ACTION_DOWN) return true;
        if(key==KeyEvent.KEYCODE_POUND) { consumePoundUp=true; setActive(false); return true; }
        if(key==KeyEvent.KEYCODE_STAR) {
            stopMovement();
            if(event.getRepeatCount()==0) { scroll=!scroll; pointer.invalidate(); Toast.makeText(this,scroll?"Derulare: sus / jos":"Deplasare cursor",Toast.LENGTH_SHORT).show(); }
            return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER) {
            stopMovement();
            if(event.getRepeatCount()==0) gesture(x,y,x,y,80);
            return true;
        }
        if(scroll && (key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN)) {
            if(event.getRepeatCount()==0) gesture(width/2,height*0.6f,width/2,
                key==KeyEvent.KEYCODE_DPAD_DOWN?height*0.25f:height*0.9f,300);
        } else {
            if(event.getRepeatCount()==0) {
                stopMovement(); heldDirection=key;
                heldSince=android.os.SystemClock.uptimeMillis();
                movePointer(key,8);
                movement.postDelayed(repeatMovement,180);
            }
        }
        return true;
    }
    private void gesture(float fromX,float fromY,float toX,float toY,long duration) {
        if(gesturePending) return;
        Path path=new Path(); path.moveTo(fromX,fromY);
        if(fromX!=toX || fromY!=toY) path.lineTo(toX,toY);
        gesturePending=true;
        boolean sent=dispatchGesture(new GestureDescription.Builder().addStroke(
            new GestureDescription.StrokeDescription(path,0,duration)).build(),new GestureResultCallback() {
                public void onCompleted(GestureDescription gesture) { gesturePending=false; Log.i("LeoPointer","gesture completed"); }
                public void onCancelled(GestureDescription gesture) { gesturePending=false; }
            },null);
        if(!sent) gesturePending=false;
    }
    public void onAccessibilityEvent(AccessibilityEvent event) { }
    public void onInterrupt() { setActive(false); }
    public void onDestroy() { if(screenWatching)unregisterReceiver(screenReceiver);screenWatching=false;if(menu!=null) menu.dismiss(); setActive(false); if(instance==this) instance=null; super.onDestroy(); }
}
