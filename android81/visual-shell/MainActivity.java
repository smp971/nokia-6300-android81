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
    private boolean offeredSetup;private boolean homeVisible;private int favoriteGeneration;private final java.util.concurrent.ExecutorService favoriteWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final android.content.BroadcastReceiver badges=new android.content.BroadcastReceiver(){public void onReceive(android.content.Context c,Intent i){screen.reloadCards();screen.invalidate();}};
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener homeChanged=new android.content.SharedPreferences.OnSharedPreferenceChangeListener(){public void onSharedPreferenceChanged(android.content.SharedPreferences p,String key){screen.reloadAppearance();screen.reloadCards();}};
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
            screen.category(state.getInt("category",0));screen.grid = state.getBoolean("grid");
            screen.selected = Math.max(0, Math.min(screen.order.length-1, state.getInt("selected")));
        }
        setContentView(screen);
        if(getIntent().hasExtra("open_app")){int id=getIntent().getIntExtra("open_app",-1);getIntent().removeExtra("open_app");if(id>=0&&id<AppCatalog.NAMES.length)screen.open(id);}
    }
    protected void onResume() {
        super.onResume();
        UnreadState.watch(this);android.content.IntentFilter filter=new android.content.IntentFilter(UnreadState.CHANGED);filter.addAction("ro.leo.visual.MUSIC_CHANGED");filter.addAction(FmService.CHANGED);registerReceiver(badges,filter);UnreadState.refresh(this);
        for(String name:new String[]{"appearance","weather","alarms"})getSharedPreferences(name,0).registerOnSharedPreferenceChangeListener(homeChanged);
        if(!offeredSetup&&!KeypadLockActivity.locked(this)&&!getSharedPreferences("setup",0).getBoolean("complete",false)){offeredSetup=true;startActivity(new Intent(this,SetupWizardActivity.class));}
        screen.reloadAppearance();
        screen.reloadCards();homeVisible=true;loadHomeFavorites();
        if(KeypadLockActivity.locked(this)) startActivity(new Intent(this,KeypadLockActivity.class));
        subscriptions = (SubscriptionManager)getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE);
        try { subscriptions.addOnSubscriptionsChangedListener(subscriptionListener); }
        catch (RuntimeException ignored) { }
        listenToPhones();
        handler.removeCallbacks(clock); handler.post(clock);
    }
    protected void onPause() {homeVisible=false;favoriteGeneration++;
        handler.removeCallbacks(clock);handler.removeCallbacks(speed);speedKey=-1;unregisterReceiver(badges);
        for(String name:new String[]{"appearance","weather","alarms"})getSharedPreferences(name,0).unregisterOnSharedPreferenceChangeListener(homeChanged);
        if (subscriptions != null) subscriptions.removeOnSubscriptionsChangedListener(subscriptionListener);
        stopPhoneListeners();
        super.onPause();
    }
    protected void onDestroy(){favoriteWorker.shutdownNow();super.onDestroy();}
    private void loadHomeFavorites(){if(!getSharedPreferences("appearance",0).getBoolean("home-favorites",false)){screen.favorites=new HomeFavorites.Entry[0];return;}final int request=++favoriteGeneration;favoriteWorker.execute(new Runnable(){public void run(){final HomeFavorites.Entry[] found=HomeFavorites.load(MainActivity.this);runOnUiThread(new Runnable(){public void run(){if(!homeVisible||request!=favoriteGeneration)return;screen.favorites=found;screen.favoriteIndex=Math.min(screen.favoriteIndex,Math.max(0,found.length-1));screen.reloadCards();screen.invalidate();}});}});}
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
                    public void onSignalStrengthsChanged(android.telephony.SignalStrength strength){if(slot>=0&&slot<2){int dbm=NetworkTelemetry.dbm(strength);if(screen.signalDbm[slot]!=dbm){screen.signalDbm[slot]=dbm;if(!screen.grid)screen.invalidate();}}}
                };
                phones.add(phone); phoneListeners.add(listener);
                phone.listen(listener, PhoneStateListener.LISTEN_SERVICE_STATE | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
            }
        } catch (RuntimeException ignored) { }
        screen.refresh();
    }
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("category",screen.category);state.putBoolean("grid", screen.grid); state.putInt("selected", screen.selected);
        super.onSaveInstanceState(state);
    }
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if(intent.hasExtra("open_app")){int id=intent.getIntExtra("open_app",-1);intent.removeExtra("open_app");if(id>=0&&id<AppCatalog.NAMES.length){screen.open(id);return;}}
        screen.grid = false;screen.shortcut=-1; screen.message = ""; screen.refresh();screen.invalidate();
    }
    public void onBackPressed() {
        if (screen.grid || !screen.message.isEmpty()) {
            screen.grid = false; screen.message = ""; screen.invalidate();
        }
    }
    public boolean dispatchKeyEvent(KeyEvent event){long start=android.os.SystemClock.elapsedRealtimeNanos();try{if(LeoActivity.isJackNavigationNoise(event))return true;return super.dispatchKeyEvent(event);}finally{if(event.getAction()==KeyEvent.ACTION_DOWN)UiMetrics.key(android.os.SystemClock.elapsedRealtimeNanos()-start);}}
    public boolean onKeyUp(int key,KeyEvent event){if(key>=KeyEvent.KEYCODE_2&&key<=KeyEvent.KEYCODE_9&&!screen.grid){boolean quick=key==KeyEvent.KEYCODE_5&&speedKey==key;handler.removeCallbacks(speed);speedKey=-1;if(quick)startActivity(new Intent(this,QuickHomeActivity.class));return true;}return super.onKeyUp(key,event);}
    public boolean onKeyDown(int key, KeyEvent event) {
        if(!screen.grid&&screen.shortcut>=4&&screen.cards.length>0){int type=screen.cards[screen.cardIndex].type;if((type==2||type==3)&&(key==KeyEvent.KEYCODE_4||key==KeyEvent.KEYCODE_5||key==KeyEvent.KEYCODE_6)){if(event.getRepeatCount()==0){if(type==3){if(key==KeyEvent.KEYCODE_5)screen.openCard();else if(screen.favorites.length>0){screen.favoriteIndex=CircularNavigation.step(screen.favoriteIndex,key==KeyEvent.KEYCODE_4?-1:1,screen.favorites.length);screen.reloadCards();screen.invalidate();}}else{PlaybackService s=PlaybackService.instance;if(s!=null&&(s.playing()||!FmService.playing)){if(key==KeyEvent.KEYCODE_5)s.toggle();else s.next(key==KeyEvent.KEYCODE_6);}else if(FmService.playing){if(key==KeyEvent.KEYCODE_5)stopService(new Intent(this,FmService.class));else if(FmService.instance!=null)FmService.instance.seek(key==KeyEvent.KEYCODE_6);}screen.reloadCards();screen.invalidate();}}return true;}}
        if(!screen.grid&&key==KeyEvent.KEYCODE_1){if(event.getRepeatCount()==0)startActivity(new Intent(this,GlobalSearchActivity.class));return true;}
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
                screen.list?"Afișare grilă":"Afișare listă","Configurări rapide","Personalizare","Reordonează aplicațiile","Media curentă","Caută în Leo","Acces rapid Home","Categorii aplicații"}, new KeypadDialog.Action() {
              public void select(int which) {
                if(which==11){new KeypadDialog(MainActivity.this,"Categorii",AppCatalog.CATEGORIES,new KeypadDialog.Action(){public void select(int at){screen.category(at);}}).show();}
                else if(which==10){startActivity(new Intent(MainActivity.this,QuickHomeActivity.class));}
                else if(which==9){startActivity(new Intent(MainActivity.this,GlobalSearchActivity.class));}
                else if(which==8){startActivity(new Intent(MainActivity.this,NowPlayingActivity.class));}
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
            android.content.SharedPreferences appearance=getSharedPreferences("appearance",0);
            compactClock=appearance.getBoolean("home-compact",false);showSims=appearance.getBoolean("home-sims",true);showUnread=appearance.getBoolean("home-unread",true);showDbm=appearance.getBoolean("home-dbm",true);
            order=AppCatalog.menuOrder(MainActivity.this,category);selected=Math.max(0,Math.min(selected,order.length-1));shortcuts=AppCatalog.shortcuts(MainActivity.this);textScale=Appearance.textScale(MainActivity.this);
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
        int category;
        void category(int value){category=Math.floorMod(value,AppCatalog.CATEGORIES.length);order=AppCatalog.menuOrder(MainActivity.this,category);selected=0;invalidate();}
        int shortcut=-1;
        HomeFavorites.Entry[] favorites=new HomeFavorites.Entry[0];int favoriteIndex;HomeCards.Card[] cards=new HomeCards.Card[0];int cardIndex;
        void reloadCards(){HomeCards.Card[] next=HomeCards.read(MainActivity.this,System.currentTimeMillis());for(int n=0;n<next.length;n++)if(next[n].type==3&&favorites.length>0){HomeFavorites.Entry f=favorites[Math.min(favoriteIndex,favorites.length-1)];next[n]=new HomeCards.Card(3,"Favorit · "+(favoriteIndex+1)+" / "+favorites.length,f.name,"4 / 6 schimbă · OK deschide");}boolean changed=!HomeCards.signature(cards).equals(HomeCards.signature(next));cards=next;cardIndex=Math.min(cardIndex,Math.max(0,cards.length-1));if(shortcut>=4+cards.length)shortcut=-1;if(changed&&!grid)invalidate();}
        void openCard(){if(cards.length==0)return;int type=cards[cardIndex].type;if(type==3){if(favorites.length>0)startActivity(new Intent(MainActivity.this,ContactsActivity.class).putExtra("contact_id",favorites[favoriteIndex].id));else startActivity(new Intent(MainActivity.this,ContactsActivity.class).putExtra("favorites",true));return;}Intent i=new Intent(MainActivity.this,type==0?WeatherActivity.class:type==1?ClockActivity.class:NowPlayingActivity.class);if(type==1)i.putExtra("tab",1);startActivity(i);}
        int[] shortcuts={0,2,6,22};
        int[] order=AppCatalog.menuOrder(MainActivity.this,0);
        float textScale=1f;
        Appearance.Palette palette=Appearance.palette(MainActivity.this);
        boolean compactClock,showSims=true,showUnread=true,showDbm=true;
        final SimpleDateFormat timeFormat=new SimpleDateFormat("HH:mm",Locale.getDefault());
        final SimpleDateFormat dateFormat=new SimpleDateFormat("EEEE, d MMMM",new Locale("ro"));
        String time = "", date = "", message = "";
        final String[] simLabels = {"SIM 1: se verifică", "SIM 2: se verifică"};
        final int[] signalDbm={Integer.MIN_VALUE,Integer.MIN_VALUE};
        final boolean[] inService={false,false};
        Screen() { super(MainActivity.this); setFocusable(true); setFocusableInTouchMode(true); requestFocus(); }
        public boolean isOpaque(){return true;}
        void refresh() {
            reloadCards();
            String previousTime=time,previousDate=date,first=simLabels[0],second=simLabels[1];
            java.util.TimeZone zone=java.util.TimeZone.getDefault();timeFormat.setTimeZone(zone);dateFormat.setTimeZone(zone);
            Date now = new Date();
            time = timeFormat.format(now);
            date = dateFormat.format(now);
            updateSims();
            // Telemetry is cached while the grid is visible; it has no pixels there.
            if(!grid&&(!time.equals(previousTime)||!date.equals(previousDate)||!simLabels[0].equals(first)||!simLabels[1].equals(second)))invalidate();
        }
        void updateSims() {
            TelephonyManager base = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
            for(int slot=0;slot<2;slot++) {
                inService[slot]=false;
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
                            inService[slot]=true;
                            int type = phone.getDataNetworkType();
                            String rat = type == TelephonyManager.NETWORK_TYPE_LTE ? " · 4G"
                                : (type == TelephonyManager.NETWORK_TYPE_UMTS || type == TelephonyManager.NETWORK_TYPE_HSDPA
                                || type == TelephonyManager.NETWORK_TYPE_HSUPA || type == TelephonyManager.NETWORK_TYPE_HSPA
                                || type == TelephonyManager.NETWORK_TYPE_HSPAP) ? " · 3G" : " · conectat";
                            simLabels[slot] = prefix + name + rat;
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
            if(!grid&&cards.length>0&&key==KeyEvent.KEYCODE_STAR){if(repeat==0){cardIndex=(cardIndex+1)%cards.length;shortcut=4+cardIndex;invalidate();}return true;}
            if(!grid&&cards.length>0&&key==KeyEvent.KEYCODE_POUND){if(repeat==0)openCard();return true;}
            if (key == KeyEvent.KEYCODE_BACK) { if (repeat == 0) { if(grid) onBackPressed(); else if(shortcut>=0){shortcut=-1;invalidate();}else open(1); } return true; }
            if (key == KeyEvent.KEYCODE_MENU) { if (repeat == 0) { if(grid) options(); else notifications(); } return true; }
            if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                if (repeat == 0) { if (grid) open(order[selected]); else if(shortcut>=4)openCard();else if(shortcut>=0)open(shortcuts[shortcut]);else { grid = true; message = ""; invalidate(); } }
                return true;
            }
            // Green opens the dialer only; this app never places a call.
            if (key == KeyEvent.KEYCODE_CALL) { if (repeat == 0) open(0); return true; }
            int digit = key - KeyEvent.KEYCODE_1;
            if(grid&&key==KeyEvent.KEYCODE_STAR){if(repeat==0)category(category-1);return true;}
            if(grid&&key==KeyEvent.KEYCODE_POUND){if(repeat==0)category(category+1);return true;}
            if(grid && key==KeyEvent.KEYCODE_0) { selected=Math.min(9,order.length-1); invalidate(); return true; }
            if (grid && digit >= 0 && digit < 9) {
                selected = Math.min(order.length-1,(selected/9)*9+digit); invalidate(); return true;
            }
            if(!grid&&key==KeyEvent.KEYCODE_DPAD_UP){if(repeat==0)startActivity(new Intent(MainActivity.this,QuickSettingsActivity.class));return true;}
            if(!grid&&key==KeyEvent.KEYCODE_DPAD_DOWN){if(repeat==0)notifications();return true;}
            if(!grid&&(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)){int count=4+cards.length;shortcut=(shortcut+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:shortcut<0?0:count-1)+count)%count;if(shortcut>=4)cardIndex=shortcut-4;invalidate();return true;}
            int delta = key == KeyEvent.KEYCODE_DPAD_UP ? -3 : key == KeyEvent.KEYCODE_DPAD_DOWN ? 3
                : key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
            if (delta != 0) {
                if (!grid) grid = true;
                else if(list) {
                    if(key==KeyEvent.KEYCODE_DPAD_UP) selected=CircularNavigation.step(selected,-1,order.length);
                    if(key==KeyEvent.KEYCODE_DPAD_DOWN) selected=CircularNavigation.step(selected,1,order.length);
                }
                else if(selected==0&&delta<0)selected=order.length-1;else if(selected==order.length-1&&delta>0)selected=0;else selected=Math.max(0,Math.min(order.length-1,selected+delta));
                message = ""; invalidate(); return true;
            }
            return false;
        }
        void open(int index) {
            if(index>=29&&index<=34){HomeAccess.record(MainActivity.this,index);startActivity(new Intent(MainActivity.this,new Class<?>[]{ThemePacksActivity.class,ReaderActivity.class,GamesActivity.class,ConnectionDashboardActivity.class,UpdatesActivity.class,CameraActivity.class}[index-29]));return;}
            if(index==28){HomeAccess.record(MainActivity.this,index);startActivity(new Intent(MainActivity.this,HelpActivity.class));return;}
            HomeAccess.record(MainActivity.this,index);
            if(index==27){startActivity(new Intent(MainActivity.this,BackupActivity.class));return;}
            if(index==26){startActivity(new Intent(MainActivity.this,WeatherActivity.class));return;}
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
                boolean cardLayout=cards.length>0,small=compactClock||cardLayout;
                round(output,10,10,230,showSims?(cardLayout?136:151):118,4,wallpaper==null?palette.card:0xcc111827);
                text(output,"L E O  ·  ACTIVE STANDBY",120,27,10,0xffeee8fa,Paint.Align.CENTER);
                text(output,time,120,small?64:86,small?36:54,Color.WHITE,Paint.Align.CENTER);
                text(output,date,120,small?87:108,13,0xfff0e5f5,Paint.Align.CENTER);
                for(int slot=0;showSims&&slot<2;slot++) {
                    String label = simLabels[slot]+(showDbm&&inService[slot]&&signalDbm[slot]!=Integer.MIN_VALUE?" · "+signalDbm[slot]+" dBm":""); p.setTypeface(regular); p.setTextSize(11);
                    while(label.length()>1 && p.measureText(label)>220) label=label.substring(0,label.length()-2)+"…";
                    text(output,label,120,(small?112:128)+slot*15,11,0xffe4cdec,Paint.Align.CENTER);
                }
                if(showUnread){if(!cardLayout)round(output,12,158,228,187,4,wallpaper==null?palette.card:0xcc111827);
                text(output,UnreadState.label(),120,cardLayout?149:177,cardLayout?10:12,Color.WHITE,Paint.Align.CENTER);}
                if(cardLayout){HomeCards.Card card=cards[cardIndex];round(output,10,156,230,211,4,wallpaper==null?palette.card:0xdd111827);if(shortcut>=4){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(palette.accent);output.drawRoundRect(10,156,230,211,4,4,p);p.setStyle(Paint.Style.FILL);}if(card.type==3&&favorites.length>0&&favorites[favoriteIndex].photo!=null){output.save();output.clipRect(15,176,45,206);Bitmap avatar=favorites[favoriteIndex].photo;float scale=Math.max(30f/avatar.getWidth(),30f/avatar.getHeight()),w=avatar.getWidth()*scale,h=avatar.getHeight()*scale;output.drawBitmap(avatar,null,new RectF(30-w/2,191-h/2,30+w/2,191+h/2),p);output.restore();}text(output,card.title,18,169,10,palette.accent,Paint.Align.LEFT);text(output,(cardIndex+1)+"/"+cards.length+" · *",223,169,9,0xffe2dbea,Paint.Align.RIGHT);String main=card.main;p.setTextSize(16);while(main.length()>1&&p.measureText(main)>(card.type==3?165:205))main=main.substring(0,main.length()-2)+"…";text(output,main,card.type==3?137:120,189,16,Color.WHITE,Paint.Align.CENTER);String detail=card.detail;p.setTextSize(10);while(detail.length()>1&&p.measureText(detail)>(card.type==3?165:205))detail=detail.substring(0,detail.length()-2)+"…";text(output,detail,card.type==3?137:120,204,10,0xffe2dbea,Paint.Align.CENTER);}
                for(int i=0;i<shortcuts.length;i++){
                    float x=33+i*58;
                    if(shortcut==i)round(output,x-26,cardLayout?216:196,x+26,cardLayout?256:251,4,palette.card);
                    output.save();output.translate(x,cardLayout?236:222);output.scale(cardLayout?.62f:.78f,cardLayout?.62f:.78f);icon(output,shortcuts[i],0,0);output.restore();
                    text(output,names[shortcuts[i]],x,263,10,Color.WHITE,Paint.Align.CENTER);
                }
            } else {
                p.setColor(palette.background); output.drawRect(0,0,240,276,p);
                p.setColor(palette.bar);output.drawRect(0,0,240,34,p);
                text(output,AppCatalog.CATEGORIES[category]+" · * / #",120,12,10,palette.accent,Paint.Align.CENTER);
                text(output,names[order[selected]],120,30,16,Color.WHITE,Paint.Align.CENTER);
                if(list) {
                    int first=Math.max(0,Math.min(Math.max(0,order.length-5),selected-2));
                    for(int row=0;row<Math.min(5,order.length);row++) {
                        int i=first+row; float y=60+row*42;
                        if(i==selected) round(output,7,y-19,233,y+19,7,palette.selection);
                        output.save(); output.translate(32,y); output.scale(.65f,.65f); icon(output,order[i],0,0); output.restore();
                        text(output,names[order[i]],57,y+5,14,i==selected?palette.ink:0xffe8e1f1,Paint.Align.LEFT);
                    }
                    text(output,(selected+1)+" / "+order.length,120,267,10,0xffd3bddf,Paint.Align.CENTER);
                } else {
                int page=selected/9;
                for(int i=page*9;i<Math.min(order.length,page*9+9);i++) {
                    int cell=i%9;
                    float x=40+(cell%3)*80, y=67+(cell/3)*76;
                    if(i==selected) round(output,x-34,y-31,x+34,y+44,10,palette.selection);
                    icon(output,order[i],x,y);
                    String label=order[i]==9?"Android":names[order[i]];p.setTextSize(10*textScale);
                    if(p.measureText(label)>70){while(label.length()>1&&p.measureText(label+"…")>70)label=label.substring(0,label.length()-1);label+="…";}
                    text(output,label,x,y+39,10,i==selected?palette.ink:0xffe8e1f1,Paint.Align.CENTER);
                }
                for(int i=0;i<(order.length+8)/9;i++) {p.setColor(i==page?0xffe0b8ff:0xff64536f); output.drawCircle(110+i*10,267,2,p);}
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
