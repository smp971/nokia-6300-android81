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
        final LockScreenArt art = new LockScreenArt(KeypadLockActivity.this);
        final SharedPreferences appearance = getSharedPreferences("appearance", 0);
        final RectF coverBounds = new RectF(19, 255, 47, 283);
        Bitmap wallpaper;
        int battery;
        boolean charging;
        String time="",date="";
        String[] preview={"",""};HomeCards.Card[] cards=new HomeCards.Card[0];
        final String[] sims={L.t("SIM 1 · se verifică"),L.t("SIM 2 · se verifică")};
        LockView(){
            super(KeypadLockActivity.this);setFocusable(true);setFocusableInTouchMode(true);requestFocus();
            if (appearance.getBoolean("lock-wallpaper", true)) {
                java.io.File image = Appearance.wallpaper(KeypadLockActivity.this);
                if (image.isFile()) wallpaper = BitmapFactory.decodeFile(image.getPath());
            }
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
                        if(service.getState()==ServiceState.STATE_IN_SERVICE){String operator=phone.getNetworkOperatorName();if(operator==null || operator.isEmpty())operator=String.valueOf(sim.getCarrierName());String technology=NetworkTelemetry.rat(NetworkTelemetry.servingType(phone));sims[slot]=prefix+operator+(technology.isEmpty()?"":" · "+technology);}
                        else sims[slot]=prefix+(service.getState()==ServiceState.STATE_EMERGENCY_ONLY?L.t("doar urgențe"):service.getState()==ServiceState.STATE_POWER_OFF?L.t("radio oprit"):L.t("fără serviciu"));
                        break;
                    }
                }catch(RuntimeException ignored){}
            }
            invalidate();
        }
        private HomeCards.Card cardOfType(int type) {
            for (HomeCards.Card card : cards) if (card.type == type) return card;
            return null;
        }

        @Override protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / 240f, getHeight() / 320f);
            art.background(canvas, palette, wallpaper);
            art.text(canvas, L.t("Taste blocate"), 12, 22, 12, palette.accent, Paint.Align.LEFT, 126);
            art.battery(canvas, battery, charging);
            art.text(canvas, sims[0], 12, 44, 10, Color.WHITE, Paint.Align.LEFT, 103);
            art.text(canvas, sims[1], 228, 44, 10, Color.WHITE, Paint.Align.RIGHT, 103);

            boolean showInfo = appearance.getBoolean("lock-info", true);
            art.clock(canvas, time, date, showInfo ? 0 : 22);
            if (showInfo) {
                art.weather(canvas, palette, cardOfType(0));
                HomeCards.Card alarm = cardOfType(1);
                if (alarm != null && !L.t("Nicio alarmă activă").equals(alarm.main)) {
                    art.text(canvas, alarm.title + " · " + alarm.main, 12, 233, 10,
                        Color.WHITE, Paint.Align.LEFT, 216);
                }
            }
            if (UnreadState.sms != 0 || UnreadState.calls != 0) {
                art.text(canvas, UnreadState.label(), 12, 247, 10, Color.WHITE, Paint.Align.LEFT, 216);
            }
            drawMediaOrNotification(canvas);
            boolean awaitingStar = armed > 0;
            art.panel(canvas, awaitingStar ? palette.selection : palette.bar, 12, 293, 228, 316, 7);
            art.text(canvas, awaitingStar ? L.t("Apasă * în 3 secunde") : L.t("Soft stânga, apoi *"),
                120, 309, 13, awaitingStar ? palette.ink : Color.WHITE, Paint.Align.CENTER, 204);
            canvas.restore();
        }

        private void drawMediaOrNotification(Canvas canvas) {
            PlaybackService music = PlaybackService.instance;
            boolean fm = FmService.playing && (music == null || !music.playing());
            boolean showMedia = appearance.getBoolean("lock-media", true)
                && (fm || music != null && !music.queue.sources.isEmpty());
            if (!showMedia && preview[0].isEmpty()) return;

            art.panel(canvas, palette.card, 12, 251, 228, 287, 8);
            if (!showMedia) {
                art.text(canvas, preview[0], 20, 266, 12, Color.WHITE, Paint.Align.LEFT, 200);
                art.text(canvas, preview[1], 20, 281, 10, palette.accent, Paint.Align.LEFT, 200);
                return;
            }

            Bitmap cover = fm ? null : music.cover();
            if (cover != null && !cover.isRecycled()) {
                paint.setColor(Color.WHITE);
                canvas.drawBitmap(cover, null, coverBounds, paint);
            } else {
                paint.setColor(palette.accent);
                canvas.drawCircle(33, 269, 12, paint);
                paint.setColor(palette.background);
                canvas.drawCircle(33, 269, 4, paint);
            }
            String title = fm ? (FmService.stationName.isEmpty()
                ? String.format(Locale.ROOT, "%.1f MHz", FmService.frequency / 1000f) : FmService.stationName)
                : PlaybackService.title;
            String action = fm ? "stop" : L.t(music.playing() ? "pauză" : "redă");
            art.text(canvas, title, 54, 267, 12, Color.WHITE, Paint.Align.LEFT, 166);
            art.text(canvas, "4 ◀   5 " + action + "   6 ▶", 54, 281, 10,
                palette.accent, Paint.Align.LEFT, 166);
        }

        public boolean onTouchEvent(android.view.MotionEvent event){return true;}
    }
}
