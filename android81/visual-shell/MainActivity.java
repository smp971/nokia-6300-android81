package ro.leo.visual;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.view.KeyEvent;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

/** Native HOME artwork and keypad layout; uses Android phone services. */
public final class MainActivity extends Activity {
    private Screen screen;
    private boolean offeredSetup;
    private final android.content.BroadcastReceiver badges=new android.content.BroadcastReceiver(){public void onReceive(android.content.Context c,Intent i){screen.invalidate();}};
    private int speedKey=-1;
    private final Runnable speed=new Runnable(){public void run(){int slot=speedKey-KeyEvent.KEYCODE_0;speedKey=-1;if(slot<2||slot>9)return;String number=getSharedPreferences("speed-dial",0).getString("number"+slot,"");if(number.isEmpty())android.widget.Toast.makeText(MainActivity.this,"Tasta "+slot+" neatribuită · Contacte → Apelare rapidă",1).show();else startActivity(new Intent(MainActivity.this,PhoneActivity.class).putExtra("number",number));}};
    private final ArrayList<TelephonyManager> phones = new ArrayList<TelephonyManager>();
    private final ArrayList<PhoneStateListener> phoneListeners = new ArrayList<PhoneStateListener>();
    private SubscriptionManager subscriptions;
    private final SubscriptionManager.OnSubscriptionsChangedListener subscriptionListener =
        new SubscriptionManager.OnSubscriptionsChangedListener() {
            public void onSubscriptionsChanged() { listenToPhones(); }
        };
    private final Handler handler = new Handler();
    private final Runnable clock = new Runnable() {
        public void run() {
            screen.refresh();
            handler.postDelayed(this, 60000 - System.currentTimeMillis() % 60000);
        }
    };
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(26, 14, 58));
        screen = new Screen();
        if (state != null) {
            screen.grid = state.getBoolean("grid");
            screen.selected = Math.max(0, Math.min(screen.names.length-1, state.getInt("selected")));
        }
        setContentView(screen);
    }
    protected void onResume() {
        super.onResume();
        UnreadState.watch(this);registerReceiver(badges,new android.content.IntentFilter(UnreadState.CHANGED));UnreadState.refresh(this);
        if(!offeredSetup&&!KeypadLockActivity.locked(this)&&!getSharedPreferences("setup",0).getBoolean("complete",false)){offeredSetup=true;startActivity(new Intent(this,SetupWizardActivity.class));}
        screen.reloadAppearance();
        if(KeypadLockActivity.locked(this)) startActivity(new Intent(this,KeypadLockActivity.class));
        subscriptions = (SubscriptionManager)getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE);
        try { subscriptions.addOnSubscriptionsChangedListener(subscriptionListener); }
        catch (RuntimeException ignored) { }
        listenToPhones();
        handler.removeCallbacks(clock); handler.post(clock);
    }
    protected void onPause() {
        handler.removeCallbacks(clock);handler.removeCallbacks(speed);speedKey=-1;unregisterReceiver(badges);
        if (subscriptions != null) subscriptions.removeOnSubscriptionsChangedListener(subscriptionListener);
        stopPhoneListeners();
        super.onPause();
    }
    private void stopPhoneListeners() {
        for (int i=0;i<phones.size();i++) {
            try { phones.get(i).listen(phoneListeners.get(i), PhoneStateListener.LISTEN_NONE); }
            catch (RuntimeException ignored) { }
        }
        phones.clear(); phoneListeners.clear();
    }
    private void listenToPhones() {
        stopPhoneListeners();
        try {
            List<SubscriptionInfo> active = subscriptions.getActiveSubscriptionInfoList();
            if (active != null) for (SubscriptionInfo info : active) {
                TelephonyManager phone = ((TelephonyManager)getSystemService(TELEPHONY_SERVICE))
                    .createForSubscriptionId(info.getSubscriptionId());
                final int slot=info.getSimSlotIndex();
                PhoneStateListener listener = new PhoneStateListener() {
                    public void onServiceStateChanged(ServiceState state) { screen.refresh(); }
                    public void onDataConnectionStateChanged(int state, int type) { screen.refresh(); }
                    public void onSignalStrengthsChanged(android.telephony.SignalStrength strength){if(slot>=0&&slot<2){int dbm=NetworkTelemetry.dbm(strength);if(screen.signalDbm[slot]!=dbm){screen.signalDbm[slot]=dbm;screen.refresh();}}}
                };
                phones.add(phone); phoneListeners.add(listener);
                phone.listen(listener, PhoneStateListener.LISTEN_SERVICE_STATE | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
            }
        } catch (RuntimeException ignored) { }
        screen.refresh();
    }
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("grid", screen.grid); state.putInt("selected", screen.selected);
        super.onSaveInstanceState(state);
    }
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        screen.grid = false;screen.shortcut=-1; screen.message = ""; screen.refresh();screen.invalidate();
    }
    public void onBackPressed() {
        if (screen.grid || !screen.message.isEmpty()) {
            screen.grid = false; screen.message = ""; screen.invalidate();
        }
    }
    public boolean dispatchKeyEvent(KeyEvent event){long start=android.os.SystemClock.elapsedRealtimeNanos();try{if(LeoActivity.isJackNavigationNoise(event))return true;return super.dispatchKeyEvent(event);}finally{if(event.getAction()==KeyEvent.ACTION_DOWN)UiMetrics.key(android.os.SystemClock.elapsedRealtimeNanos()-start);}}
    public boolean onKeyUp(int key,KeyEvent event){if(key>=KeyEvent.KEYCODE_2&&key<=KeyEvent.KEYCODE_9&&!screen.grid){handler.removeCallbacks(speed);speedKey=-1;return true;}return super.onKeyUp(key,event);}
    public boolean onKeyDown(int key, KeyEvent event) {
        if(!screen.grid&&key==KeyEvent.KEYCODE_0){if(event.getRepeatCount()==0)startActivity(new Intent(this,NowPlayingActivity.class));return true;}
        if(!screen.grid&&key>=KeyEvent.KEYCODE_2&&key<=KeyEvent.KEYCODE_9){if(event.getRepeatCount()==0){speedKey=key;handler.postDelayed(speed,650);}return true;}
        if(key==KeyEvent.KEYCODE_MENU && event.getScanCode()==212) {
            if(event.getRepeatCount()==0) options();
            return true;
        }
        if (screen.key(key, event.getRepeatCount())) return true;
        return super.onKeyDown(key, event);
    }
    private void options() {
        new KeypadDialog(this,"Opțiuni",new String[]{"Schimbă fundalul", "Mouse din taste", "Notificări", "Despre",
                screen.list?"Afișare grilă":"Afișare listă","Configurări rapide","Personalizare","Reordonează aplicațiile","Acum se redă"}, new KeypadDialog.Action() {
              public void select(int which) {
                if(which==8){startActivity(new Intent(MainActivity.this,NowPlayingActivity.class));}
                else if (which == 0) {
                    startActivity(new Intent(MainActivity.this,WallpaperActivity.class));
                }
                else if (which == 1) {
                    if (!LeoPointerService.toggle()) new AlertDialog.Builder(MainActivity.this)
                        .setMessage("Serviciul de cursor nu este activat.").setPositiveButton("OK", null).show();
                }
                else if (which == 2) notifications();
                else if (which == 3) new AlertDialog.Builder(MainActivity.this).setTitle("Leo Visual")
                    .setMessage("OK: aplicații. Soft stânga: notificări. În modul mouse: săgeți, OK pentru clic, # pentru oprire, * pentru derulare.")
                    .setPositiveButton("OK", null).show();
                else if(which==7)startActivity(new Intent(MainActivity.this,PersonalizationActivity.class).putExtra("order",true));
                else if(which==6)startActivity(new Intent(MainActivity.this,PersonalizationActivity.class));
                else if(which==5)startActivity(new Intent(MainActivity.this,QuickSettingsActivity.class));
                else if (which == 4) {
                    screen.list=!screen.list;
                    getSharedPreferences("appearance",MODE_PRIVATE).edit().putBoolean("list",screen.list).apply();
                    screen.invalidate();
                }
              }
            }).show();
    }
    private void notifications() {
        startActivity(new Intent(this,NotificationsActivity.class));
    }
    private final class Screen extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF box = new RectF();
        final Path path = new Path();
        final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
        final Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        final String[] names = AppCatalog.NAMES;
        final String[] packages = {"com.android.dialer", "com.android.contacts", "com.android.messaging", "com.android.deskclock", "com.android.calculator2", "com.android.gallery3d", "com.android.music", "com.android.documentsui", "com.android.settings"};

        Bitmap wallpaper;
        long wallpaperStamp=-1,wallpaperSize=-1;
        int theme;
        void reloadAppearance() {
            order=AppCatalog.order(MainActivity.this);shortcuts=AppCatalog.shortcuts(MainActivity.this);textScale=Appearance.textScale(MainActivity.this);
            theme=Math.max(0,Math.min(Appearance.COLORS.length-1,getSharedPreferences("appearance",0).getInt("theme",getSharedPreferences("appearance",0).getBoolean("warm",false)?1:0)));
            palette=Appearance.palette(MainActivity.this);getWindow().setStatusBarColor(palette.bar);getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(palette.background));
            java.io.File file=Appearance.wallpaper(MainActivity.this);long stamp=file.lastModified(),size=file.length();
            if(stamp!=wallpaperStamp||size!=wallpaperSize){
                if(wallpaper!=null){wallpaper.recycle();wallpaper=null;}
                if(file.isFile())wallpaper=BitmapFactory.decodeFile(file.getPath());
                wallpaperStamp=stamp;wallpaperSize=size;
            }
            invalidate();
        }
        boolean grid;
        boolean warm=getSharedPreferences("appearance",MODE_PRIVATE).getBoolean("warm",false);
        boolean list=getSharedPreferences("appearance",MODE_PRIVATE).getBoolean("list",false);
        int selected;
        int shortcut=-1;
        int[] shortcuts={0,2,6,22};
        int[] order=AppCatalog.parse("");
        float textScale=1f;
        Appearance.Palette palette=Appearance.palette(MainActivity.this);
        String time = "", date = "", message = "";
        final String[] simLabels = {"SIM 1: se verifică", "SIM 2: se verifică"};
        final int[] signalDbm={Integer.MIN_VALUE,Integer.MIN_VALUE};
        Screen() { super(MainActivity.this); setFocusable(true); setFocusableInTouchMode(true); requestFocus(); }
        public boolean isOpaque(){return true;}
        void refresh() {
            String previousTime=time,previousDate=date,first=simLabels[0],second=simLabels[1];
            Date now = new Date();
            time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now);
            date = new SimpleDateFormat("EEEE, d MMMM", new Locale("ro")).format(now);
            updateSims();
            // Telemetry is cached while the grid is visible; it has no pixels there.
            if(!grid&&(!time.equals(previousTime)||!date.equals(previousDate)||!simLabels[0].equals(first)||!simLabels[1].equals(second)))invalidate();
        }
        void updateSims() {
            TelephonyManager base = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
            for(int slot=0;slot<2;slot++) {
                String prefix = "SIM " + (slot+1) + ": ";
                try {
                    int sim = base.getSimState(slot);
                    String status = sim == TelephonyManager.SIM_STATE_ABSENT ? "lipsă"
                        : sim == TelephonyManager.SIM_STATE_PIN_REQUIRED ? "PIN necesar"
                        : sim == TelephonyManager.SIM_STATE_PUK_REQUIRED ? "PUK necesar"
                        : sim == TelephonyManager.SIM_STATE_READY ? "se conectează" : "indisponibil";
                    simLabels[slot] = prefix + status;
                    if (sim != TelephonyManager.SIM_STATE_READY) continue;
                    List<SubscriptionInfo> active = ((SubscriptionManager)getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE)).getActiveSubscriptionInfoList();
                    if(active == null) continue;
                    for(SubscriptionInfo info : active) if(info.getSimSlotIndex() == slot) {
                        TelephonyManager phone = base.createForSubscriptionId(info.getSubscriptionId());
                        String carrier = phone.getNetworkOperatorName();
                        if(carrier == null || carrier.isEmpty()) carrier = info.getCarrierName() == null ? "" : info.getCarrierName().toString();
                        ServiceState service = phone.getServiceState();
                        String name = carrier.isEmpty() ? "operator" : carrier;
                        if(service == null) simLabels[slot] = prefix + "se verifică";
                        else if(service.getState() == ServiceState.STATE_IN_SERVICE) {
                            int type = phone.getDataNetworkType();
                            String rat = type == TelephonyManager.NETWORK_TYPE_LTE ? " · 4G"
                                : (type == TelephonyManager.NETWORK_TYPE_UMTS || type == TelephonyManager.NETWORK_TYPE_HSDPA
                                || type == TelephonyManager.NETWORK_TYPE_HSUPA || type == TelephonyManager.NETWORK_TYPE_HSPA
                                || type == TelephonyManager.NETWORK_TYPE_HSPAP) ? " · 3G" : " · conectat";
                            simLabels[slot] = prefix + name + rat+(signalDbm[slot]!=Integer.MIN_VALUE?" · "+signalDbm[slot]+" dBm":"");
                        } else if(service.getState() == ServiceState.STATE_EMERGENCY_ONLY) simLabels[slot] = prefix + "doar urgențe";
                        else if(service.getState() == ServiceState.STATE_POWER_OFF) simLabels[slot] = prefix + "radio oprit";
                        else simLabels[slot] = prefix + "fără serviciu";
                        break;
                    }
                } catch(SecurityException ignored) { simLabels[slot] = prefix + "acces neacordat"; }
                catch(RuntimeException ignored) { simLabels[slot] = prefix + "indisponibil"; }
            }
        }
        boolean key(int key, int repeat) {
            if (key == KeyEvent.KEYCODE_BACK) { if (repeat == 0) { if(grid) onBackPressed(); else if(shortcut>=0){shortcut=-1;invalidate();}else open(1); } return true; }
            if (key == KeyEvent.KEYCODE_MENU) { if (repeat == 0) { if(grid) options(); else notifications(); } return true; }
            if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                if (repeat == 0) { if (grid) open(order[selected]); else if(shortcut>=0)open(shortcuts[shortcut]);else { grid = true; message = ""; invalidate(); } }
                return true;
            }
            // Green opens the dialer only; this app never places a call.
            if (key == KeyEvent.KEYCODE_CALL) { if (repeat == 0) open(0); return true; }
            int digit = key - KeyEvent.KEYCODE_1;
            if(grid && key==KeyEvent.KEYCODE_0) { selected=9; invalidate(); return true; }
            if (grid && digit >= 0 && digit < 9) {
                selected = digit; invalidate(); return true;
            }
            if(!grid&&key==KeyEvent.KEYCODE_DPAD_UP){if(repeat==0)startActivity(new Intent(MainActivity.this,QuickSettingsActivity.class));return true;}
            if(!grid&&key==KeyEvent.KEYCODE_DPAD_DOWN){if(repeat==0)notifications();return true;}
            if(!grid&&(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)){shortcut=(shortcut+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:shortcut<0?0:3)+4)%4;invalidate();return true;}
            int delta = key == KeyEvent.KEYCODE_DPAD_UP ? -3 : key == KeyEvent.KEYCODE_DPAD_DOWN ? 3
                : key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
            if (delta != 0) {
                if (!grid) grid = true;
                else if(list) {
                    if(key==KeyEvent.KEYCODE_DPAD_UP) selected=Math.max(0,selected-1);
                    if(key==KeyEvent.KEYCODE_DPAD_DOWN) selected=Math.min(names.length-1,selected+1);
                }
                else selected=Math.max(0,Math.min(names.length-1,selected+delta));
                message = ""; invalidate(); return true;
            }
            return false;
        }
        void open(int index) {
            if(index==25){startActivity(new Intent(MainActivity.this,FmActivity.class));return;}
            if(index==24){startActivity(new Intent(MainActivity.this,DiagnosticActivity.class));return;}
            if(index==23){startActivity(new Intent(MainActivity.this,TerminalActivity.class));return;}
            if(index==22){startActivity(new Intent(MainActivity.this,EmailActivity.class));return;}
            if(index==19){startActivity(new Intent(MainActivity.this,BrowserActivity.class));return;}
            if(index==20){startActivity(new Intent(MainActivity.this,QuickSettingsActivity.class));return;}
            if(index==21){startActivity(new Intent(MainActivity.this,SetupWizardActivity.class));return;}
            if(index==5) {startActivity(new Intent(MainActivity.this,GalleryActivity.class));return;}
            if(index==6 || index==12) {startActivity(new Intent(MainActivity.this,MediaActivity.class).putExtra("kind",index==12?"video":"audio"));return;}
            if(index==8) {startActivity(new Intent(MainActivity.this,SettingsActivity.class));return;}
            if(index==13) {startActivity(new Intent(MainActivity.this,RecorderActivity.class));return;}
            if(index==14) {startActivity(new Intent(MainActivity.this,ConverterActivity.class));return;}
            if(index==15) {startActivity(new Intent(MainActivity.this,DownloadsActivity.class));return;}
            if(index==16) {startActivity(new Intent(MainActivity.this,WallpaperActivity.class));return;}
            if(index==17) {startActivity(new Intent(MainActivity.this,ContactsActivity.class).putExtra("favorites",true));return;}
            if(index==18) {startActivity(new Intent(MainActivity.this,KeypadLockActivity.class));return;}
            if(index==7) { startActivity(new Intent(MainActivity.this,FilesActivity.class)); return; }
            if(index==11) { startActivity(new Intent(MainActivity.this,CalendarActivity.class)); return; }
            if(index==0) { startActivity(new Intent(MainActivity.this,PhoneActivity.class)); return; }
            if(index==1) { startActivity(new Intent(MainActivity.this,ContactsActivity.class)); return; }
            if(index==2) { startActivity(new Intent(MainActivity.this,MessagesActivity.class)); return; }
            if(index==4) { startActivity(new Intent(MainActivity.this,CalculatorActivity.class)); return; }
            if(index==10) { startActivity(new Intent(MainActivity.this,NotesActivity.class)); return; }
            if(index==3) { startActivity(new Intent(MainActivity.this,ClockActivity.class)); return; }
            if(index==9) { startActivity(new Intent(MainActivity.this,AndroidAppsActivity.class)); return; }
            Intent intent;
            if (index == 0) intent = new Intent(Intent.ACTION_DIAL);
            else if (index == 1) intent = new Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI);
            else if (index == 8) intent = new Intent(Settings.ACTION_SETTINGS);
            else {
                intent = getPackageManager().getLaunchIntentForPackage(packages[index]);
                if (intent == null) {
                    // Resolve launcher activities for OEM/AOSP package variants.
                    List<ResolveInfo> apps = getPackageManager().queryIntentActivities(
                        new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
                    for (ResolveInfo info : apps) {
                        String pkg = info.activityInfo.packageName;
                        if (pkg.equals(packages[index]) || (index == 4 && pkg.contains("calculator"))) {
                            intent = new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(pkg, info.activityInfo.name)); break;
                        }
                    }
                }
            }
            if (intent == null || intent.resolveActivity(getPackageManager()) == null) {
                message = names[index] + ": indisponibil"; invalidate(); return;
            }
            try { startActivity(intent); }
            catch (RuntimeException ignored) { message = "Nu se poate deschide"; invalidate(); }
        }
        void text(Canvas c, String s, float x, float y, float size, int color, Paint.Align align) {
            p.setShader(null); p.setStyle(Paint.Style.FILL); p.setTypeface(regular);
            p.setTextSize(size*(size<26?textScale:1f));while(p.measureText(s)>220&&p.getTextSize()>9)p.setTextSize(p.getTextSize()-1); p.setColor(color); p.setTextAlign(align); c.drawText(s, x, y, p);
        }
        void round(Canvas c, float l, float t, float r, float b, float radius, int color) {
            p.setShader(null); p.setColor(color); p.setStyle(Paint.Style.FILL); box.set(l,t,r,b); c.drawRoundRect(box,Math.min(radius,4),Math.min(radius,4),p);
        }
        void line(Canvas c,float x1,float y1,float x2,float y2) { c.drawLine(x1,y1,x2,y2,p); }
        final IconArt artwork=new IconArt();
        void icon(Canvas c,int index,float x,float y){artwork.draw(c,index,x,y);int count=index==0?UnreadState.calls:index==2?UnreadState.sms:0;if(count>0){round(c,x+5,y-28,x+29,y-8,8,0xffd93657);text(c,count>9?"9+":""+count,x+17,y-13,11,Color.WHITE,Paint.Align.CENTER);}}
        protected void onDraw(Canvas output) {
            long drawStart=android.os.SystemClock.elapsedRealtimeNanos();super.onDraw(output);
            output.drawColor(palette.background);
            output.save(); output.scale(getWidth()/240f,getHeight()/302f);
            p.setStyle(Paint.Style.FILL); p.setShader(null);
            if (!grid) {
                if(wallpaper!=null) {p.setColor(Color.WHITE);output.drawBitmap(wallpaper,null,new RectF(0,0,240,302),p);p.setColor(0x55000000);output.drawRect(0,0,240,302,p);}
                round(output,10,10,230,151,4,wallpaper==null?palette.card:0xcc111827);
                text(output,"L E O  ·  ACTIVE STANDBY",120,27,10,0xffeee8fa,Paint.Align.CENTER);
                text(output,time,120,86,54,Color.WHITE,Paint.Align.CENTER);
                text(output,date,120,108,13,0xfff0e5f5,Paint.Align.CENTER);
                for(int slot=0;slot<2;slot++) {
                    String label = simLabels[slot]; p.setTypeface(regular); p.setTextSize(11);
                    while(label.length()>1 && p.measureText(label)>220) label=label.substring(0,label.length()-2)+"…";
                    text(output,label,120,128+slot*15,11,0xffe4cdec,Paint.Align.CENTER);
                }
                round(output,12,158,228,187,4,wallpaper==null?palette.card:0xcc111827);
                text(output,UnreadState.label(),120,177,12,Color.WHITE,Paint.Align.CENTER);
                for(int i=0;i<shortcuts.length;i++){
                    float x=33+i*58;
                    if(shortcut==i)round(output,x-26,196,x+26,251,4,palette.card);
                    output.save();output.translate(x,222);output.scale(.78f,.78f);icon(output,shortcuts[i],0,0);output.restore();
                    text(output,names[shortcuts[i]],x,263,10,Color.WHITE,Paint.Align.CENTER);
                }
            } else {
                p.setColor(palette.background); output.drawRect(0,0,240,276,p);
                p.setColor(palette.bar);output.drawRect(0,0,240,34,p);
                text(output,names[order[selected]],120,25,18,Color.WHITE,Paint.Align.CENTER);
                if(list) {
                    int first=Math.max(0,Math.min(names.length-5,selected-2));
                    for(int row=0;row<5;row++) {
                        int i=first+row; float y=60+row*42;
                        if(i==selected) round(output,7,y-19,233,y+19,7,palette.selection);
                        output.save(); output.translate(32,y); output.scale(.65f,.65f); icon(output,order[i],0,0); output.restore();
                        text(output,names[order[i]],57,y+5,14,i==selected?palette.ink:0xffe8e1f1,Paint.Align.LEFT);
                    }
                    text(output,(selected+1)+" / "+names.length,120,267,10,0xffd3bddf,Paint.Align.CENTER);
                } else {
                int page=selected/9;
                for(int i=page*9;i<Math.min(names.length,page*9+9);i++) {
                    int cell=i%9;
                    float x=40+(cell%3)*80, y=67+(cell/3)*76;
                    if(i==selected) round(output,x-34,y-31,x+34,y+44,10,palette.selection);
                    icon(output,order[i],x,y);
                    String label=order[i]==9?"Android":names[order[i]];p.setTextSize(10*textScale);
                    if(p.measureText(label)>70){while(label.length()>1&&p.measureText(label+"…")>70)label=label.substring(0,label.length()-1);label+="…";}
                    text(output,label,x,y+39,10,i==selected?palette.ink:0xffe8e1f1,Paint.Align.CENTER);
                }
                for(int i=0;i<(names.length+8)/9;i++) {p.setColor(i==page?0xffe0b8ff:0xff64536f); output.drawCircle(110+i*10,267,2,p);}
                }
            }
            if(!message.isEmpty()) {
                round(output,5,240,235,274,6,0xff302339);
                text(output,message,120,261,12,Color.WHITE,Paint.Align.CENTER);
            }
            p.setColor(palette.bar); output.drawRect(0,276,240,302,p);
            text(output,grid?"Opțiuni":"Notificări",8,293,11,Color.WHITE,Paint.Align.LEFT);
            text(output,grid?"Deschide":shortcut>=0?"Deschide":"Meniu",120,293,12,palette.selection,Paint.Align.CENTER);
            text(output,grid||shortcut>=0?"Înapoi":"Contacte",232,293,11,Color.WHITE,Paint.Align.RIGHT);
            output.restore();UiMetrics.draw(android.os.SystemClock.elapsedRealtimeNanos()-drawStart);
        }
    }
}
