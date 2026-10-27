package ro.leo.visual;
import ro.leo.i18n.L;

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
    private String foregroundPackage="",foregroundClass="";private boolean helpHeld,helpShown;private final android.os.Handler helpTimer=new android.os.Handler();private final Runnable holdHelp=new Runnable(){public void run(){if(helpHeld&&!KeypadLockActivity.locked(LeoPointerService.this)&&!InCallActivity.visible&&!AlarmRingService.ringing&&!PowerMenuActivity.visible){helpShown=true;setActive(false);if(menu!=null)menu.dismiss();ContextHelp.open(LeoPointerService.this,foregroundClass);}}};
    private int menuGeneration;
    private boolean screenWatching;
    private final android.content.BroadcastReceiver screenReceiver=new android.content.BroadcastReceiver(){
        public void onReceive(android.content.Context c,android.content.Intent intent){
            boolean busy=AlarmRingService.ringing;
            try{busy|=((android.telecom.TelecomManager)getSystemService(TELECOM_SERVICE)).isInCall();}catch(RuntimeException ignored){}
            if(android.content.Intent.ACTION_SCREEN_OFF.equals(intent.getAction())){
                menuGeneration++;helpHeld=false;helpTimer.removeCallbacks(holdHelp);
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
    private long nextEdgeScroll;
    static void prepareForCall(){if(instance!=null){instance.setActive(false);if(instance.menu!=null)instance.menu.dismiss();}}
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
        edgeScroll(key);
        Log.i("LeoPointer","move x="+x+" y="+y);
    }
    static int edgeDirection(int key,int y,int height){
        if(key==KeyEvent.KEYCODE_DPAD_UP&&y<=2)return -1;
        if(key==KeyEvent.KEYCODE_DPAD_DOWN&&y>=height-3)return 1;
        return 0;
    }
    private void edgeScroll(int key){
        int direction=edgeDirection(key,y,height);
        long now=android.os.SystemClock.uptimeMillis();
        if(!active||scroll||direction==0||gesturePending||now<nextEdgeScroll)return;
        // Keep swipes away from status/softkey bars; a held key repeats at most once per 700 ms.
        nextEdgeScroll=now+700;
        gesture(width*.5f,height*(direction>0?.72f:.28f),width*.5f,
                height*(direction>0?.28f:.72f),300);
        Log.i("LeoPointer","edge scroll "+direction);
    }
    protected void onServiceConnected() {
        instance=this;
        manager=(WindowManager)getSystemService(WINDOW_SERVICE);
        if(!screenWatching){android.content.IntentFilter f=new android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF);f.addAction(android.content.Intent.ACTION_SCREEN_ON);registerReceiver(screenReceiver,f);screenWatching=true;}
    }
    static boolean connected(){return instance!=null;}
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
        menuGeneration++;
        stopMovement();
        if(menu!=null && menu.isShowing()) { menu.dismiss(); return; }
        menu=new KeypadDialog(new ContextThemeWrapper(this,android.R.style.Theme_Material_NoActionBar),
            L.t("Meniu Leo"),new String[]{L.t("Notificări"),active?L.t("Oprește mouse-ul"):L.t("Mouse din taste"),L.t("Acasă"),L.t("Setări Leo"),L.t("Blochează tastele"),L.t("Configurări rapide"),L.t("Închide meniul"),L.t("Acum se redă"),L.t("Aplicații recente"),L.t("Închide aplicația curentă"),L.t("Pornire și oprire"),L.t("Transferuri Bluetooth"),L.t("Comenzile paginii"),L.t("Spațiu de lucru"),L.t("Leo Studio")},new KeypadDialog.Action() {
                public void select(int which) {if(which==13||which==14){setActive(false);startActivity(new android.content.Intent(LeoPointerService.this,which==13?WorkspaceActivity.class:PremiumStudioActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));return;}if(which==11){setActive(false);startActivity(new android.content.Intent(LeoPointerService.this,BluetoothTransfersActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));return;}if(which==12){setActive(false);ContextHelp.open(LeoPointerService.this,foregroundClass);return;}
                    if(which==10){openPowerMenu();return;}
                    if(which==9){closeForeground();return;}
                    if(which==8){setActive(false);startActivity(new android.content.Intent(LeoPointerService.this,RecentAppsActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));return;}
                    if(which==7) startActivity(new android.content.Intent(LeoPointerService.this,NowPlayingActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                    else if(which==0) startActivity(new android.content.Intent(LeoPointerService.this,NotificationsActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                    else if(which==1) setActive(!active);
                    else if(which==2) performGlobalAction(GLOBAL_ACTION_HOME);
                    else if(which==3) startActivity(new android.content.Intent(LeoPointerService.this,SettingsActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                    else if(which==5) startActivity(new android.content.Intent(LeoPointerService.this,QuickSettingsActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                    else if(which==4) {setActive(false);startActivity(new android.content.Intent(LeoPointerService.this,KeypadLockActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));}
                }
            });
        menu.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        menu.show();
        Log.i("LeoPointer","global menu opened");
    }
    private void closeForeground(){
        setActive(false);
        if(getPackageName().equals(foregroundPackage)){performGlobalAction(GLOBAL_ACTION_HOME);return;}
        if(foregroundPackage.isEmpty()||foregroundPackage.equals("com.android.systemui")||foregroundPackage.equals("com.android.phone")||foregroundPackage.equals("com.android.server.telecom")||AlarmRingService.ringing){Toast.makeText(this,L.t("Fereastră de sistem protejată"),Toast.LENGTH_SHORT).show();return;}
        try{if(((android.telecom.TelecomManager)getSystemService(TELECOM_SERVICE)).isInCall()){Toast.makeText(this,L.t("Încheie mai întâi apelul"),Toast.LENGTH_SHORT).show();return;}}catch(RuntimeException ignored){return;}
        final String packageName=foregroundPackage;
        final int ticket=++menuGeneration;
        new Thread(new Runnable(){public void run(){TaskActions.Entry found=null;try{for(TaskActions.Entry task:TaskActions.recent(LeoPointerService.this))if(task.packageName.equals(packageName)){found=task;break;}}catch(RuntimeException ignored){}final TaskActions.Entry task=found;
            movement.post(new Runnable(){public void run(){if(ticket!=menuGeneration||instance!=LeoPointerService.this)return;if(task==null){Toast.makeText(LeoPointerService.this,L.t("Fereastra curentă nu poate fi închisă"),Toast.LENGTH_SHORT).show();return;}
                menu=new KeypadDialog(new ContextThemeWrapper(LeoPointerService.this,android.R.style.Theme_Material_NoActionBar),L.t("Închizi ")+task.label+"?",new String[]{L.t("Păstrează"),L.t("Închide fereastra")},new KeypadDialog.Action(){public void select(int at){if(at==1)new Thread(new Runnable(){public void run(){try{TaskActions.close(LeoPointerService.this,task.id);}catch(Exception e){movement.post(new Runnable(){public void run(){Toast.makeText(LeoPointerService.this,L.t("Închidere indisponibilă"),Toast.LENGTH_SHORT).show();}});}}},"LeoCloseTask").start();}});
                menu.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);menu.show();
            }});
        }},"LeoTaskLookup").start();
    }
    private void setActive(boolean enable) {
        stopMovement();
        if(enable==active) return;
        if(enable) {
            android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();
            manager.getDefaultDisplay().getRealMetrics(metrics);
            width=metrics.widthPixels; height=metrics.heightPixels; x=width/2; y=height/2;
            scroll=false;nextEdgeScroll=0;
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
            Toast.makeText(this,L.t("Mouse: săgeți · margini sus/jos: derulare · OK clic · # oprire"),Toast.LENGTH_LONG).show();
        } else {
            active=false;
            if(pointer!=null) { try { manager.removeView(pointer); } catch(RuntimeException ignored) { } pointer=null; }
        }
    }
    protected boolean onKeyEvent(KeyEvent event) {
        if(LeoActivity.isJackNavigationNoise(event)) return true;
        if(PowerMenuActivity.visible)return false;
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
            if(InCallActivity.visible)return false;
            if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){helpHeld=true;helpShown=false;helpTimer.postDelayed(holdHelp,650);}else if(event.getAction()==KeyEvent.ACTION_UP){boolean shortPress=helpHeld&&!helpShown;helpHeld=false;helpTimer.removeCallbacks(holdHelp);if(shortPress)globalMenu();}
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
            if(event.getRepeatCount()==0) { scroll=!scroll; pointer.invalidate(); Toast.makeText(this,scroll?L.t("Derulare: sus / jos"):L.t("Deplasare cursor"),Toast.LENGTH_SHORT).show(); }
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
    private void openPowerMenu(){setActive(false);if(menu!=null)menu.dismiss();sendBroadcast(new android.content.Intent(android.content.Intent.ACTION_CLOSE_SYSTEM_DIALOGS));startActivity(new android.content.Intent(this,PowerMenuActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP));}
    private boolean systemPowerDialog(AccessibilityEvent event){
        String pkg=String.valueOf(event.getPackageName()),name=String.valueOf(event.getClassName());
        if(!pkg.equals("android")&&!pkg.equals("com.android.systemui"))return false;
        if(name.contains("GlobalActions"))return true;
        int off=getResources().getIdentifier("global_action_power_off","string","android"),restart=getResources().getIdentifier("global_action_restart","string","android");
        return off!=0&&restart!=0&&event.getText().contains(getString(off))&&event.getText().contains(getString(restart));
    }
    public void onAccessibilityEvent(AccessibilityEvent event) {if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&event.getClassName()!=null&&(menu==null||!menu.isShowing()))foregroundClass=event.getClassName().toString();if(event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return;if(systemPowerDialog(event)){openPowerMenu();return;}if((menu==null||!menu.isShowing())&&event.getPackageName()!=null)foregroundPackage=event.getPackageName().toString();}
    public void onInterrupt() { setActive(false); }
    public void onDestroy() {helpHeld=false;helpTimer.removeCallbacksAndMessages(null); if(screenWatching)unregisterReceiver(screenReceiver);screenWatching=false;if(menu!=null) menu.dismiss(); setActive(false); if(instance==this) instance=null; super.onDestroy(); }
}
