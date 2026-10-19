package ro.leo.visual;
import android.content.*;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Handler;
import android.os.BatteryManager;
import android.graphics.*;
import android.telephony.*;
import android.view.KeyEvent;
import android.view.View;
import java.util.*;

/** Accidental-key guard for Leo, not a PIN or Android security lock. */
public final class KeypadLockActivity extends LeoActivity {
    private long armed;
    private LockView screen;
    private final Handler handler=new Handler();
    private final Runnable disarm=new Runnable(){public void run(){armed=0;if(screen!=null)screen.invalidate();}};
    private final Runnable minute=new Runnable(){public void run(){screen.refresh();handler.postDelayed(this,60000-System.currentTimeMillis()%60000);}};
    private final BroadcastReceiver changes=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){
        if(Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())){
            int scale=intent.getIntExtra(BatteryManager.EXTRA_SCALE,100);
            screen.battery=Math.max(0,Math.min(100,intent.getIntExtra(BatteryManager.EXTRA_LEVEL,0)*100/Math.max(1,scale)));
            screen.charging=intent.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0;
        }
        screen.refresh();
    }};
    static boolean visible;
    protected void onResume(){
        super.onResume();visible=true;IntentFilter f=new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(Intent.ACTION_TIME_CHANGED);f.addAction(Intent.ACTION_TIMEZONE_CHANGED);f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction("android.intent.action.SERVICE_STATE");f.addAction("android.intent.action.SIM_STATE_CHANGED");f.addAction("android.intent.action.ACTION_SUBINFO_RECORD_UPDATED");
        f.addAction(UnreadState.CHANGED);UnreadState.watch(this);UnreadState.refresh(this);
        registerReceiver(changes,f);handler.removeCallbacks(minute);handler.post(minute);
    }
    protected void onPause(){visible=false;unregisterReceiver(changes);handler.removeCallbacks(minute);handler.removeCallbacks(disarm);armed=0;super.onPause();}
    protected void onDestroy(){if(screen.wallpaper!=null)screen.wallpaper.recycle();super.onDestroy();}
    static boolean locked(Context c) { return c.getSharedPreferences("keyguard",0).getBoolean("locked",false); }
    static void unlock(Context c) { c.getSharedPreferences("keyguard",0).edit().putBoolean("locked",false).apply(); }
    protected void onCreate(Bundle state) {
        super.onCreate(state);getSharedPreferences("keyguard",0).edit().putBoolean("locked",true).apply();
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        screen=new LockView();setContentView(screen);
    }
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key=event.getKeyCode();
        if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0) {
            if(key==KeyEvent.KEYCODE_MENU) { armed=SystemClock.elapsedRealtime();handler.removeCallbacks(disarm);handler.postDelayed(disarm,3000);screen.invalidate(); }
            else if(key==KeyEvent.KEYCODE_STAR && armed>0 && SystemClock.elapsedRealtime()-armed<=3000) { unlock(this);finish(); }
            else {armed=0;screen.invalidate();}
        }
        return true;
    }
    public void onBackPressed() { }
    private final class LockView extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        final Path mountain=new Path();
        final Shader background;
        Bitmap wallpaper;
        int battery;
        boolean charging;
        String time="",date="";
        final String[] sims={"SIM 1 · se verifică","SIM 2 · se verifică"};
        LockView(){
            super(KeypadLockActivity.this);setFocusable(true);setFocusableInTouchMode(true);requestFocus();
            int theme=Math.max(0,Math.min(Appearance.COLORS.length-1,getSharedPreferences("appearance",0).getInt("theme",getSharedPreferences("appearance",0).getBoolean("warm",false)?1:0)));
            background=new LinearGradient(0,0,240,320,Appearance.COLORS[theme],null,Shader.TileMode.CLAMP);
            if(Appearance.wallpaper(KeypadLockActivity.this).isFile())wallpaper=BitmapFactory.decodeFile(Appearance.wallpaper(KeypadLockActivity.this).getPath());
        }
        void refresh(){
            Date now=new Date();time=android.text.format.DateFormat.getTimeFormat(KeypadLockActivity.this).format(now);
            date=new java.text.SimpleDateFormat("EEEE, d MMMM",new Locale("ro")).format(now);
            TelephonyManager base=(TelephonyManager)getSystemService(TELEPHONY_SERVICE);
            List<SubscriptionInfo> active=null;
            try{active=SubscriptionManager.from(KeypadLockActivity.this).getActiveSubscriptionInfoList();}catch(RuntimeException ignored){}
            for(int slot=0;slot<2;slot++){
                String prefix="SIM "+(slot+1)+" · ";sims[slot]=prefix+"indisponibil";
                try{
                    int state=base.getSimState(slot);
                    if(state==TelephonyManager.SIM_STATE_ABSENT){sims[slot]=prefix+"lipsă";continue;}
                    if(state==TelephonyManager.SIM_STATE_PIN_REQUIRED || state==TelephonyManager.SIM_STATE_PUK_REQUIRED){sims[slot]=prefix+"SIM blocat";continue;}
                    sims[slot]=prefix+"se conectează";
                    if(active!=null)for(SubscriptionInfo sim:active)if(sim.getSimSlotIndex()==slot){
                        TelephonyManager phone=base.createForSubscriptionId(sim.getSubscriptionId());ServiceState service=phone.getServiceState();
                        if(service==null)break;
                        if(service.getState()==ServiceState.STATE_IN_SERVICE){String operator=phone.getNetworkOperatorName();if(operator==null || operator.isEmpty())operator=String.valueOf(sim.getCarrierName());sims[slot]=prefix+operator+(phone.getDataNetworkType()==TelephonyManager.NETWORK_TYPE_LTE?" · 4G":"");}
                        else sims[slot]=prefix+(service.getState()==ServiceState.STATE_EMERGENCY_ONLY?"doar urgențe":service.getState()==ServiceState.STATE_POWER_OFF?"radio oprit":"fără serviciu");
                        break;
                    }
                }catch(RuntimeException ignored){}
            }
            invalidate();
        }
        void label(Canvas c,String value,float x,float y,int size,int color,Paint.Align align){
            paint.setShader(null);paint.setColor(color);paint.setTextAlign(align);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(size);
            while(paint.measureText(value)>220 && paint.getTextSize()>10)paint.setTextSize(paint.getTextSize()-1);
            c.drawText(value,x,y,paint);
        }
        protected void onDraw(Canvas c){
            c.save();c.scale(getWidth()/240f,getHeight()/320f);
            paint.setShader(background);c.drawRect(0,0,240,320,paint);paint.setShader(null);
            if(wallpaper!=null){paint.setColor(Color.WHITE);c.drawBitmap(wallpaper,null,new RectF(0,0,240,320),paint);paint.setColor(0x66000000);c.drawRect(0,0,240,320,paint);}
            else{paint.setColor(0x5530204a);mountain.reset();mountain.moveTo(0,238);mountain.lineTo(68,155);mountain.lineTo(135,234);mountain.lineTo(188,176);mountain.lineTo(240,224);mountain.lineTo(240,320);mountain.lineTo(0,320);mountain.close();c.drawPath(mountain,paint);}
            label(c,"LEO",12,23,11,0xffefe7fa,Paint.Align.LEFT);
            label(c,(charging?"+ ":"")+battery+"%",198,23,11,Color.WHITE,Paint.Align.RIGHT);
            paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.2f);c.drawRoundRect(new RectF(205,13,228,24),2,2,paint);paint.setStyle(Paint.Style.FILL);c.drawRect(229,16,231,21,paint);c.drawRect(207,15,207+19*battery/100f,22,paint);
            label(c,time,120,91,49,Color.WHITE,Paint.Align.CENTER);
            label(c,date,120,115,13,0xfff0e8f7,Paint.Align.CENTER);
            label(c,sims[0],120,147,12,0xfff0e8f7,Paint.Align.CENTER);label(c,sims[1],120,167,12,0xfff0e8f7,Paint.Align.CENTER);
            label(c,UnreadState.label(),120,190,12,Color.WHITE,Paint.Align.CENTER);
            paint.setColor(0x99150e24);c.drawRoundRect(new RectF(12,207,228,283),10,10,paint);
            paint.setColor(0xfff5eefb);c.drawRoundRect(new RectF(112,205,128,220),3,3,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);c.drawArc(new RectF(114,196,126,213),180,180,false,paint);paint.setStyle(Paint.Style.FILL);
            label(c,armed>0?"Apasă acum *":"Taste blocate",120,237,17,Color.WHITE,Paint.Align.CENTER);
            label(c,armed>0?"Ai 3 secunde":"Soft stânga, apoi *",120,259,12,0xffe3d4ef,Paint.Align.CENTER);
            label(c,"Deblochează",10,311,12,Color.WHITE,Paint.Align.LEFT);label(c,"*",230,311,15,Color.WHITE,Paint.Align.RIGHT);
            c.restore();
        }
        public boolean onTouchEvent(android.view.MotionEvent event){return true;}
    }
}
