package ro.leo.visual;
import ro.leo.i18n.L;
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
    private int previewIndex;
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
        f.addAction(FmService.CHANGED);f.addAction("ro.leo.visual.MUSIC_CHANGED");f.addAction(UnreadState.CHANGED);f.addAction(LeoNotificationService.CHANGED);UnreadState.watch(this);UnreadState.refresh(this);
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
            PlaybackService music=PlaybackService.instance;
            if(armed==0&&FmService.playing&&(music==null||!music.playing())&&(key==KeyEvent.KEYCODE_4||key==KeyEvent.KEYCODE_5||key==KeyEvent.KEYCODE_6)){if(key==KeyEvent.KEYCODE_5)stopService(new Intent(this,FmService.class));else if(FmService.instance!=null)FmService.instance.seek(key==KeyEvent.KEYCODE_6);screen.refresh();return true;}
            if(armed==0&&music!=null&&(key==KeyEvent.KEYCODE_4||key==KeyEvent.KEYCODE_5||key==KeyEvent.KEYCODE_6)){if(key==KeyEvent.KEYCODE_5)music.toggle();else music.next(key==KeyEvent.KEYCODE_6);screen.refresh();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_UP){previewIndex+=key==KeyEvent.KEYCODE_DPAD_DOWN?1:-1;screen.refresh();return true;}
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
        Bitmap wallpaper;
        int battery;
        boolean charging;
        String time="",date="";
        String[] preview={"",""};HomeCards.Card[] cards=new HomeCards.Card[0];
        final String[] sims={L.t("SIM 1 · se verifică"),L.t("SIM 2 · se verifică")};
        LockView(){
            super(KeypadLockActivity.this);setFocusable(true);setFocusableInTouchMode(true);requestFocus();
            int theme=Math.max(0,Math.min(Appearance.COLORS.length-1,getSharedPreferences("appearance",0).getInt("theme",getSharedPreferences("appearance",0).getBoolean("warm",false)?1:0)));
            if(getSharedPreferences("appearance",0).getBoolean("lock-wallpaper",true)&&Appearance.wallpaper(KeypadLockActivity.this).isFile())wallpaper=BitmapFactory.decodeFile(Appearance.wallpaper(KeypadLockActivity.this).getPath());
        }
        void refresh(){
            Date now=new Date();time=android.text.format.DateFormat.getTimeFormat(KeypadLockActivity.this).format(now);
            date=new java.text.SimpleDateFormat("EEEE, d MMMM",L.locale()).format(now);
            cards=HomeCards.lockInfo(KeypadLockActivity.this,System.currentTimeMillis());
            preview=LeoNotificationService.preview(KeypadLockActivity.this,previewIndex);
            TelephonyManager base=(TelephonyManager)getSystemService(TELEPHONY_SERVICE);
            List<SubscriptionInfo> active=null;
            try{active=SubscriptionManager.from(KeypadLockActivity.this).getActiveSubscriptionInfoList();}catch(RuntimeException ignored){}
            for(int slot=0;slot<2;slot++){
                String prefix="SIM "+(slot+1)+" · ";sims[slot]=prefix+L.t("indisponibil");
                try{
                    int state=base.getSimState(slot);
                    if(state==TelephonyManager.SIM_STATE_ABSENT){sims[slot]=prefix+L.t("lipsă");continue;}
                    if(state==TelephonyManager.SIM_STATE_PIN_REQUIRED || state==TelephonyManager.SIM_STATE_PUK_REQUIRED){sims[slot]=prefix+L.t("SIM blocat");continue;}
                    sims[slot]=prefix+L.t("se conectează");
                    if(active!=null)for(SubscriptionInfo sim:active)if(sim.getSimSlotIndex()==slot){
                        TelephonyManager phone=base.createForSubscriptionId(sim.getSubscriptionId());ServiceState service=phone.getServiceState();
                        if(service==null)break;
                        if(service.getState()==ServiceState.STATE_IN_SERVICE){String operator=phone.getNetworkOperatorName();if(operator==null || operator.isEmpty())operator=String.valueOf(sim.getCarrierName());sims[slot]=prefix+operator+(phone.getDataNetworkType()==TelephonyManager.NETWORK_TYPE_LTE?" · 4G":"");}
                        else sims[slot]=prefix+(service.getState()==ServiceState.STATE_EMERGENCY_ONLY?L.t("doar urgențe"):service.getState()==ServiceState.STATE_POWER_OFF?L.t("radio oprit"):L.t("fără serviciu"));
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
            paint.setShader(null);paint.setColor(palette.background);c.drawRect(0,0,240,320,paint);paint.setShader(null);
            if(wallpaper!=null){paint.setColor(Color.WHITE);c.drawBitmap(wallpaper,null,new RectF(0,0,240,320),paint);paint.setColor(0x66000000);c.drawRect(0,0,240,320,paint);}
            else{paint.setColor(0x5530204a);mountain.reset();mountain.moveTo(0,238);mountain.lineTo(68,155);mountain.lineTo(135,234);mountain.lineTo(188,176);mountain.lineTo(240,224);mountain.lineTo(240,320);mountain.lineTo(0,320);mountain.close();c.drawPath(mountain,paint);}
            label(c,"LEO",12,23,11,0xffefe7fa,Paint.Align.LEFT);
            label(c,(charging?"+ ":"")+battery+"%",198,23,11,Color.WHITE,Paint.Align.RIGHT);
            paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.2f);c.drawRoundRect(new RectF(205,13,228,24),2,2,paint);paint.setStyle(Paint.Style.FILL);c.drawRect(229,16,231,21,paint);c.drawRect(207,15,207+19*battery/100f,22,paint);
            label(c,time,120,76,43,Color.WHITE,Paint.Align.CENTER);label(c,date,120,98,13,0xffd1e2e5,Paint.Align.CENTER);
            label(c,sims[0],120,119,11,0xffd1e2e5,Paint.Align.CENTER);label(c,sims[1],120,136,11,0xffd1e2e5,Paint.Align.CENTER);label(c,UnreadState.label(),120,154,11,Color.WHITE,Paint.Align.CENTER);
            if(getSharedPreferences("appearance",0).getBoolean("lock-info",true)){for(int i=0;i<2;i++){float x=12+i*112;paint.setColor(palette.card);c.drawRoundRect(x,163,x+104,207,7,7,paint);String title=i==0?L.t("Meteo · cache"):L.t("Următoarea alarmă"),value=L.t("Indisponibil");for(HomeCards.Card card:cards)if(card.type==i){value=card.main;break;}paint.setTextSize(11);int limit=paint.breakText(value,true,94,null);if(limit<value.length())value=value.substring(0,Math.max(0,limit-1))+"…";label(c,title,x+7,178,9,palette.accent,Paint.Align.LEFT);label(c,value,x+7,197,11,Color.WHITE,Paint.Align.LEFT);}}
            PlaybackService music=PlaybackService.instance;boolean fm=FmService.playing&&(music==null||!music.playing()),hasMusic=music!=null&&!music.queue.sources.isEmpty(),showMedia=getSharedPreferences("appearance",0).getBoolean("lock-media",true)&&(hasMusic||fm);
            if(showMedia||!preview[0].isEmpty()){paint.setColor(palette.card);c.drawRoundRect(12,215,228,263,7,7,paint);if(showMedia){Bitmap art=fm?null:music.cover();if(art!=null&&!art.isRecycled()){paint.setColor(Color.WHITE);c.drawBitmap(art,null,new RectF(18,221,54,257),paint);}else{paint.setColor(palette.accent);c.drawCircle(36,239,15,paint);paint.setColor(palette.background);c.drawCircle(36,239,6,paint);}String title=fm?(FmService.stationName.isEmpty()?String.format(Locale.ROOT,"%.1f MHz",FmService.frequency/1000f):FmService.stationName):PlaybackService.title;paint.setTextSize(12);int n=paint.breakText(title,true,160,null);if(n<title.length())title=title.substring(0,Math.max(0,n-1))+"…";label(c,title,62,233,12,Color.WHITE,Paint.Align.LEFT);label(c,"4 ◀  5 "+(fm?"stop":music.playing()?L.t("pauză"):L.t("redă"))+"  6 ▶",62,251,11,palette.accent,Paint.Align.LEFT);}else{label(c,preview[0],120,233,12,Color.WHITE,Paint.Align.CENTER);label(c,preview[1],120,251,11,palette.accent,Paint.Align.CENTER);}}
            paint.setColor(palette.bar);c.drawRoundRect(12,270,228,302,7,7,paint);label(c,armed>0?L.t("Apasă * în 3 secunde"):L.t("Soft stânga, apoi *"),120,291,14,Color.WHITE,Paint.Align.CENTER);
            label(c,L.t("Deblochează"),10,315,11,Color.WHITE,Paint.Align.LEFT);label(c,"*",230,315,14,Color.WHITE,Paint.Align.RIGHT);
            c.restore();
        }
        public boolean onTouchEvent(android.view.MotionEvent event){return true;}
    }
}
