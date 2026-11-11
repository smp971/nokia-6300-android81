package ro.leo.visual;
import ro.leo.i18n.L;

import android.app.Activity;
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
    private static final int RIGHT_SOFTKEY_SCAN = 158;
    private Screen screen;
    private final NavigationPace navigationPace=new NavigationPace();
    private boolean offeredSetup;private boolean homeVisible;private int favoriteGeneration;private final java.util.concurrent.ExecutorService favoriteWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final android.content.BroadcastReceiver badges=new android.content.BroadcastReceiver(){public void onReceive(android.content.Context c,Intent i){screen.reloadCards();screen.invalidate();}};
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener homeChanged=new android.content.SharedPreferences.OnSharedPreferenceChangeListener(){public void onSharedPreferenceChanged(android.content.SharedPreferences p,String key){screen.reloadAppearance();screen.reloadCards();}};
    private int speedKey=-1;
    private final Runnable speed=new Runnable(){public void run(){int slot=speedKey-KeyEvent.KEYCODE_0;speedKey=-1;if(slot<2||slot>9)return;int app=HomeKeyActions.app(MainActivity.this,slot);if(app>=0){HomeAccess.open(MainActivity.this,app);return;}String number=getSharedPreferences("speed-dial",0).getString("number"+slot,"");if(number.isEmpty())android.widget.Toast.makeText(MainActivity.this,L.t("Tasta ")+slot+L.t(" neatribuită · Contacte → Apelare rapidă"),1).show();else startActivity(new Intent(MainActivity.this,PhoneActivity.class).putExtra("number",number));}};
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
            handler.postDelayed(this,android.os.SystemClock.elapsedRealtime()<180000&&(!screen.inService[0]||!screen.inService[1])?2000:60000-System.currentTimeMillis()%60000);
        }
    };
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if(ShellHealth.start(this))return;
        getWindow().setStatusBarColor(Color.rgb(26, 14, 58));
        screen = new Screen();
        if (state != null) {
            screen.category(state.getInt("category",0));screen.grid = state.getBoolean("grid");
            screen.selected = Math.max(0, Math.min(screen.order.length-1, state.getInt("selected")));
            int[] savedFocus = state.getIntArray("category_app_focus");
            if (savedFocus != null && savedFocus.length == screen.categoryAppFocus.length) {
                System.arraycopy(savedFocus, 0, screen.categoryAppFocus, 0, savedFocus.length);
                screen.restoreAppFocus(savedFocus[screen.category]);
            }
        }
        setContentView(screen);
        if(getIntent().getBooleanExtra("open_menu",false)){getIntent().removeExtra("open_menu");screen.grid=true;}
        if(getIntent().getBooleanExtra("edit_home",false)){getIntent().removeExtra("edit_home");screen.startEditor();}
        if(getIntent().hasExtra("open_app")){int id=getIntent().getIntExtra("open_app",-1);getIntent().removeExtra("open_app");if(id>=0&&id<AppCatalog.NAMES.length)screen.open(id);}
    }
    protected void onResume() {
        super.onResume();
        UnreadState.watch(this);android.content.IntentFilter filter=new android.content.IntentFilter(UnreadState.CHANGED);filter.addAction("ro.leo.visual.MUSIC_CHANGED");filter.addAction(FmService.CHANGED);filter.addAction(LeoNotificationService.CHANGED);registerReceiver(badges,filter);UnreadState.refresh(this);
        for(String name:new String[]{"appearance","weather","alarms","accessibility"})getSharedPreferences(name,0).registerOnSharedPreferenceChangeListener(homeChanged);
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
        for(String name:new String[]{"appearance","weather","alarms","accessibility"})getSharedPreferences(name,0).unregisterOnSharedPreferenceChangeListener(homeChanged);
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
                PhoneStateListener listener = new PhoneStateListener() {
                    public void onServiceStateChanged(ServiceState state) { screen.refresh(); }
                    public void onDataConnectionStateChanged(int state, int type) { screen.refresh(); }
                };
                phones.add(phone); phoneListeners.add(listener);
                phone.listen(listener, PhoneStateListener.LISTEN_SERVICE_STATE | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);
            }
        } catch (RuntimeException ignored) { }
        screen.refresh();
    }
    protected void onSaveInstanceState(Bundle state) {
        state.putInt("category",screen.category);state.putBoolean("grid", screen.grid); state.putInt("selected", screen.selected);
        screen.categoryAppFocus[screen.category] = screen.focusedApp();
        state.putIntArray("category_app_focus", screen.categoryAppFocus.clone());
        super.onSaveInstanceState(state);
    }
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if(intent.getBooleanExtra("open_menu",false)){intent.removeExtra("open_menu");if(screen.editing){screen.leaveEditor();return;}screen.grid=true;screen.shortcut=-1;screen.message="";screen.invalidate();return;}
        if(intent.getBooleanExtra("edit_home",false)){intent.removeExtra("edit_home");screen.startEditor();return;}
        if(intent.hasExtra("open_app")){int id=intent.getIntExtra("open_app",-1);intent.removeExtra("open_app");if(id>=0&&id<AppCatalog.NAMES.length){screen.open(id);return;}}
        screen.grid = false;screen.shortcut=-1; screen.message = ""; screen.refresh();screen.invalidate();
    }
    public void onBackPressed() {
        if(screen.editing){screen.leaveEditor();return;}
        if (screen.grid || screen.shortcut >= 0 || !screen.message.isEmpty()) {
            screen.grid = false;
            screen.shortcut = -1;
            screen.message = "";
            screen.invalidate();
        }
    }
    public boolean dispatchKeyEvent(KeyEvent event){long start=android.os.SystemClock.elapsedRealtimeNanos();try{if(LeoActivity.isJackNavigationNoise(event)||navigationPace.consume(this,event))return true;return super.dispatchKeyEvent(event);}finally{if(event.getAction()==KeyEvent.ACTION_DOWN)UiMetrics.key(android.os.SystemClock.elapsedRealtimeNanos()-start);}}
    @Override public boolean onKeyUp(int key, KeyEvent event) {
        if (key >= KeyEvent.KEYCODE_2 && key <= KeyEvent.KEYCODE_9 && !screen.grid) {
            boolean shortPress = speedKey == key && !event.isCanceled();
            handler.removeCallbacks(speed);
            speedKey = -1;
            if (shortPress && key == KeyEvent.KEYCODE_5) {
                startActivity(new Intent(this, QuickHomeActivity.class));
            } else if (shortPress && getSharedPreferences("appearance", 0).getBoolean("home-type-search", false)) {
                startActivity(new Intent(this, GlobalSearchActivity.class)
                    .putExtra("query", String.valueOf(key - KeyEvent.KEYCODE_0)).putExtra("home_digits", true));
            } else if (shortPress) {
                startActivity(new Intent(this, PhoneActivity.class)
                    .putExtra("number", String.valueOf(key - KeyEvent.KEYCODE_0)));
            }
            return true;
        }
        return super.onKeyUp(key, event);
    }
    public boolean onKeyDown(int key, KeyEvent event) {
        if(screen.editing)return screen.editorKey(key,event.getRepeatCount());
        if(!screen.grid&&screen.shortcut>=4&&screen.cardAvailable()){int type=screen.cards[screen.cardIndex].type;if((type==2||type==3)&&(key==KeyEvent.KEYCODE_4||key==KeyEvent.KEYCODE_5||key==KeyEvent.KEYCODE_6)){if(event.getRepeatCount()==0){if(type==3){if(key==KeyEvent.KEYCODE_5)screen.openCard();else if(screen.favorites.length>0){screen.favoriteIndex=CircularNavigation.step(screen.favoriteIndex,key==KeyEvent.KEYCODE_4?-1:1,screen.favorites.length);screen.reloadCards();screen.invalidate();}}else{PlaybackService s=PlaybackService.instance;if(s!=null&&(s.playing()||!FmService.playing)){if(key==KeyEvent.KEYCODE_5)s.toggle();else s.next(key==KeyEvent.KEYCODE_6);}else if(FmService.playing){if(key==KeyEvent.KEYCODE_5)stopService(new Intent(this,FmService.class));else if(FmService.instance!=null)FmService.instance.seek(key==KeyEvent.KEYCODE_6);}screen.reloadCards();screen.invalidate();}}return true;}}
        if(!screen.grid&&key==KeyEvent.KEYCODE_1){if(event.getRepeatCount()==0)startActivity(new Intent(this,GlobalSearchActivity.class).putExtra("apps_first",true));return true;}
        if(!screen.grid&&key==KeyEvent.KEYCODE_0){if(event.getRepeatCount()==0)startActivity(new Intent(this,NowPlayingActivity.class));return true;}
        if(!screen.grid&&key>=KeyEvent.KEYCODE_2&&key<=KeyEvent.KEYCODE_9){if(event.getRepeatCount()==0){speedKey=key;handler.postDelayed(speed,650);}return true;}
        if(key==KeyEvent.KEYCODE_MENU && event.getScanCode()==212) {
            if(event.getRepeatCount()==0) options();
            return true;
        }
        int dashboardKey = key == KeyEvent.KEYCODE_BACK && event.getScanCode() == RIGHT_SOFTKEY_SCAN
            ? KeyEvent.KEYCODE_SOFT_RIGHT : key;
        if (screen.key(dashboardKey, event.getRepeatCount())) return true;
        return super.onKeyDown(key, event);
    }
    private void options() {
        if(screen.grid)appOptions();
        else personalize(null);
    }
    private KeypadDialog shellDialog(String title,String[] items,KeypadDialog.Action action){
        return new KeypadDialog(this,title,items,action,false,screen.palette);
    }
    private void appOptions() {
        final int app=screen.focusedApp();
        ArrayList<String> items=new ArrayList<>();
        items.add(L.t("Personalizare"));
        items.add(L.t("Caută în Leo"));
        items.add(L.t("Reordonează aplicațiile"));
        if(app>=0)items.add(L.t("Atribuie la soft dreapta"));
        if(!screen.simple)items.add(L.t("Categorii aplicații"));
        shellDialog(L.t("Opțiuni aplicații"),items.toArray(new String[0]),new KeypadDialog.Action(){
            public void select(int at){
                if(at==0)personalize(new Runnable(){public void run(){appOptions();}});
                else if(at==1)startActivity(new Intent(MainActivity.this,GlobalSearchActivity.class));
                else if(at==2)startActivity(new Intent(MainActivity.this,PersonalizationActivity.class).putExtra("order",true));
                else if(at==3&&app>=0)assignRightApp(app);
                else showCategories();
            }
        }).show();
    }
    private void assignRightApp(int app){
        getSharedPreferences("appearance",MODE_PRIVATE).edit()
            .putInt(HomePersonalization.RIGHT_APP_KEY,app).apply();
        android.widget.Toast.makeText(this,L.t("Soft dreapta")+" · "+AppCatalog.NAMES[app],
            android.widget.Toast.LENGTH_SHORT).show();
    }
    private void personalize(Runnable back) {
        new HomePersonalization(this,new java.util.function.Supplier<Appearance.Palette>(){
            public Appearance.Palette get(){return screen.palette;}
        },new Runnable(){public void run(){screen.startEditor();}},back).show();
    }
    private void showCategories() {
        shellDialog(L.t("Categorii"), AppCatalog.CATEGORIES, new KeypadDialog.Action() {
            public void select(int category) { screen.category(category); }
        }).initialSelection(screen.category).onBack(new Runnable() {
            public void run() { appOptions(); }
        }).show();
    }
    private void notifications() {
        if(!LeoPointerService.openAndroidPanel(false))startActivity(new Intent(this,NotificationsActivity.class));
    }
    final class Screen extends View {
        final LeoHome home=new LeoHome();
        HomeCanvasLayout homeLayout=new HomeCanvasLayout(MainActivity.this);
        boolean editing;int editItem;
        void startEditor(){LeoPointerService.pauseForHomeEditor();homeLayout=new HomeCanvasLayout(MainActivity.this);editing=true;grid=false;shortcut=-1;editItem=2;invalidate();}
        void leaveEditor(){shellDialog(L.t("Editor Home"),new String[]{L.t("Continuă editarea"),L.t("Salvează și închide"),L.t("Renunță la modificări")},new KeypadDialog.Action(){public void select(int i){if(i==1)homeLayout.save(MainActivity.this);if(i>0){editing=false;homeLayout=new HomeCanvasLayout(MainActivity.this);invalidate();}}}).show();}
        boolean editorKey(int key,int repeat){
            if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){homeLayout.alignment[editItem]=0;homeLayout.x[editItem]+=key==KeyEvent.KEYCODE_DPAD_LEFT?-2:2;}
            else if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN)homeLayout.y[editItem]+=key==KeyEvent.KEYCODE_DPAD_UP?-2:2;
            else if(key==KeyEvent.KEYCODE_STAR||key==KeyEvent.KEYCODE_POUND){if(repeat==0)editItem=HomeCanvasLayout.nextItem(editItem,key==KeyEvent.KEYCODE_STAR?-1:1);}
            else if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){if(repeat==0)editorOptions();}
            else if(key==KeyEvent.KEYCODE_MENU||key==KeyEvent.KEYCODE_SOFT_LEFT){if(repeat==0){homeLayout.save(MainActivity.this);editing=false;}}
            else if(key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_SOFT_RIGHT){if(repeat==0)leaveEditor();}
            invalidate();return true;
        }
        void editorOptions(){new KeypadDialog(MainActivity.this,L.t(HomeCanvasLayout.NAMES[editItem]),new String[]{L.t("Mărește")+" · "+homeLayout.size[editItem]+"%",L.t("Micșorează"),L.t("Rotește elementul")+" · "+homeLayout.rotation[editItem]*90+"°",L.t("Aliniere: liber / stânga / centru / dreapta"),L.t("Arată / ascunde"),L.t("Resetează elementul"),L.t("Resetează tot ecranul"),L.t("Salvează și închide")},new KeypadDialog.Action(){public void select(int i){if(i==0)homeLayout.size[editItem]=Math.min(150,homeLayout.size[editItem]+5);else if(i==1)homeLayout.size[editItem]=Math.max(50,homeLayout.size[editItem]-5);else if(i==2)homeLayout.rotation[editItem]=(homeLayout.rotation[editItem]+1)%4;else if(i==3)homeLayout.alignment[editItem]=(homeLayout.alignment[editItem]+1)%4;else if(i==4)homeLayout.visible[editItem]=!homeLayout.visible[editItem];else if(i==5)homeLayout.reset(editItem);else if(i==6){new KeypadDialog(MainActivity.this,L.t("Resetează tot ecranul"),new String[]{L.t("Anulează"),L.t("Resetează")},new KeypadDialog.Action(){public void select(int choice){if(choice==1)for(int item=0;item<HomeCanvasLayout.COUNT;item++)homeLayout.reset(item);invalidate();}}).show();}else if(i==7){homeLayout.save(MainActivity.this);editing=false;}invalidate();}}).show();}
        boolean homePart(Canvas c,int id,float l,float t,float r,float b){return homeLayout.begin(c,id,l,t,r,b,editing);}
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final String[] names = AppCatalog.NAMES;
        final String[] packages = {"com.android.dialer", "com.android.contacts", "com.android.messaging", "com.android.deskclock", "com.android.calculator2", "com.android.gallery3d", "com.android.music", "com.android.documentsui", "com.android.settings"};

        Bitmap wallpaper;
        long wallpaperStamp=-1,wallpaperSize=-1;boolean highContrast;
        void reloadAppearance() {
            android.content.SharedPreferences appearance=getSharedPreferences("appearance",0);
            list=appearance.getBoolean("list",false);
            rightApp=HomePersonalization.rightApp(appearance);
            simple=ShellMode.simple(MainActivity.this);
            if(!editing)homeLayout=new HomeCanvasLayout(MainActivity.this);
            compactClock=appearance.getBoolean("home-compact",false);showSims=appearance.getBoolean("home-sims",true);showUnread=appearance.getBoolean("home-unread",true);
            int focused = focusedApp();
            order = AppCatalog.menuOrder(MainActivity.this, category);
            selected = Math.max(0, Math.min(selected, order.length - 1));
            restoreAppFocus(focused);
            shortcuts=AppCatalog.shortcuts(MainActivity.this);textScale=Appearance.textScale(MainActivity.this);
            palette=Appearance.palette(MainActivity.this);
            SystemChrome.apply(MainActivity.this,palette);getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(palette.background));
            java.io.File file=Appearance.wallpaper(MainActivity.this);long stamp=file.lastModified(),size=file.length();
            boolean contrast=AccessibilityPrefs.contrast(MainActivity.this);if(stamp!=wallpaperStamp||size!=wallpaperSize||highContrast!=contrast){highContrast=contrast;
                if(wallpaper!=null){wallpaper.recycle();wallpaper=null;}
                if(file.isFile()&&!highContrast)wallpaper=BitmapFactory.decodeFile(file.getPath());
                wallpaperStamp=stamp;wallpaperSize=size;
            }
            invalidate();
        }
        boolean grid;
        boolean simple=ShellMode.simple(MainActivity.this);
        int rightApp=1;
        boolean list=getSharedPreferences("appearance",MODE_PRIVATE).getBoolean("list",false);
        int selected;
        int category;
        final int[] categoryAppFocus = new int[AppCatalog.CATEGORIES.length];
        { java.util.Arrays.fill(categoryAppFocus, -1); }
        int focusedApp() {
            return selected >= 0 && selected < order.length ? order[selected] : -1;
        }
        void restoreAppFocus(int app) {
            for (int i = 0; i < order.length; i++) {
                if (order[i] == app) {
                    selected = i;
                    return;
                }
            }
        }
        void category(int value) {
            categoryAppFocus[category] = focusedApp();
            category = Math.floorMod(value, AppCatalog.CATEGORIES.length);
            order = AppCatalog.menuOrder(MainActivity.this, category);
            selected = 0;
            restoreAppFocus(categoryAppFocus[category]);
            invalidate();
        }
        int shortcut=-1;
        HomeFavorites.Entry[] favorites=new HomeFavorites.Entry[0];int favoriteIndex;HomeCards.Card[] cards=new HomeCards.Card[0];int cardIndex;
        boolean cardAvailable() { return cards.length > 0 && homeLayout.visible[5]; }

        void reloadCards() {
            boolean selectedCard = shortcut >= 4 && cards.length > 0;
            int previousType = cards.length == 0 ? -1 : cards[cardIndex].type;
            HomeCards.Card[] next = getSharedPreferences("appearance", 0).getBoolean("home-contextual", true)
                ? HomeContext.read(MainActivity.this, System.currentTimeMillis())
                : HomeCards.read(MainActivity.this, System.currentTimeMillis());
            for (int i = 0; i < next.length; i++) {
                if (next[i].type == 3 && favorites.length > 0) {
                    HomeFavorites.Entry favorite = favorites[Math.min(favoriteIndex, favorites.length - 1)];
                    next[i] = new HomeCards.Card(3, L.t("Favorit · ") + (favoriteIndex + 1) + " / " + favorites.length,
                        favorite.name, L.t("4 / 6 schimbă · OK deschide"));
                }
            }
            boolean changed = !HomeCards.signature(cards).equals(HomeCards.signature(next));
            cards = next;
            cardIndex = 0;
            boolean found = false;
            for (int i = 0; i < cards.length; i++) {
                if (cards[i].type == previousType) { cardIndex = i; found = true; break; }
            }
            if (selectedCard) shortcut = found && cardAvailable() ? 4 + cardIndex : -1;
            if (shortcut >= 0 && shortcut < 4 && !homeLayout.visible[6]) shortcut = -1;
            if (changed && !grid) invalidate();
        }

        private void moveHomeFocus(int direction) {
            int first = homeLayout.visible[6] ? 0 : 4;
            int end = cardAvailable() ? 4 + cards.length : 4;
            if (end <= first) return;
            if (shortcut < first || shortcut >= end) shortcut = direction > 0 ? first : end - 1;
            else shortcut = first + Math.floorMod(shortcut - first + direction, end - first);
            if (shortcut >= 4) cardIndex = shortcut - 4;
            invalidate();
        }
        void openCard(){if(!cardAvailable())return;int type=cards[cardIndex].type;if(type==5){startActivity(new Intent(MainActivity.this,NotificationsActivity.class));return;}if(type==3){if(favorites.length>0)startActivity(new Intent(MainActivity.this,ContactsActivity.class).putExtra("contact_id",favorites[favoriteIndex].id));else startActivity(new Intent(MainActivity.this,ContactsActivity.class).putExtra("favorites",true));return;}Intent i=new Intent(MainActivity.this,type==0?WeatherActivity.class:type==1?ClockActivity.class:type==4?DailyAgendaActivity.class:NowPlayingActivity.class);if(type==1)i.putExtra("tab",1);startActivity(i);}
        int[] shortcuts={0,2,6,22};
        int[] order=AppCatalog.menuOrder(MainActivity.this,0);
        float textScale=1f;
        Appearance.Palette palette=Appearance.palette(MainActivity.this);
        boolean compactClock,showSims=true,showUnread=true;
        final SimpleDateFormat timeFormat=new SimpleDateFormat("HH:mm",Locale.getDefault());
        final SimpleDateFormat dateFormat=new SimpleDateFormat("EEEE, d MMMM",L.locale());
        String time = "", date = "", message = "", networkBanner="L E O · ACTIVE STANDBY";
        final String[] simLabels = {L.t("se verifică"), L.t("se verifică")};
        final boolean[] inService={false,false};
        Screen() { super(MainActivity.this); setFocusable(true); setFocusableInTouchMode(true); requestFocus(); }
        public boolean isOpaque(){return true;}
        void refresh() {
            reloadCards();
            String previousTime=time,previousDate=date,first=simLabels[0],second=simLabels[1],previousBanner=networkBanner;
            java.util.TimeZone zone=java.util.TimeZone.getDefault();timeFormat.setTimeZone(zone);dateFormat.setTimeZone(zone);
            Date now = new Date();
            time = timeFormat.format(now);
            date = dateFormat.format(now);
            updateSims();
            networkBanner=NetworkStartup.label(NetworkStartup.stage(),inService[0]||inService[1],Settings.Global.getInt(getContentResolver(),Settings.Global.AIRPLANE_MODE_ON,0)!=0,android.os.SystemClock.elapsedRealtime());
            // Telemetry is cached while the grid is visible; it has no pixels there.
            if(!grid&&(!time.equals(previousTime)||!date.equals(previousDate)||!simLabels[0].equals(first)||!simLabels[1].equals(second)||!networkBanner.equals(previousBanner)))invalidate();
        }
        void updateSims() {
            TelephonyManager base = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
            for(int slot=0;slot<2;slot++) {
                inService[slot]=false;
                try {
                    int sim = base.getSimState(slot);
                    String status = sim == TelephonyManager.SIM_STATE_ABSENT ? L.t("lipsă")
                        : sim == TelephonyManager.SIM_STATE_PIN_REQUIRED ? L.t("PIN necesar")
                        : sim == TelephonyManager.SIM_STATE_PUK_REQUIRED ? L.t("PUK necesar")
                        : sim == TelephonyManager.SIM_STATE_READY ? L.t("se conectează") : L.t("indisponibil");
                    simLabels[slot] = status;
                    if (sim != TelephonyManager.SIM_STATE_READY) continue;
                    List<SubscriptionInfo> active = ((SubscriptionManager)getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE)).getActiveSubscriptionInfoList();
                    if(active == null) continue;
                    for(SubscriptionInfo info : active) if(info.getSimSlotIndex() == slot) {
                        TelephonyManager phone = base.createForSubscriptionId(info.getSubscriptionId());
                        String carrier = phone.getNetworkOperatorName();
                        if(carrier == null || carrier.isEmpty()) carrier = info.getCarrierName() == null ? "" : info.getCarrierName().toString();
                        ServiceState service = phone.getServiceState();
                        String name = carrier.isEmpty() ? "operator" : carrier;
                        if(service == null) simLabels[slot] = L.t("se verifică");
                        else if(service.getState() == ServiceState.STATE_IN_SERVICE) {
                            inService[slot]=true;
                            int type = phone.getDataNetworkType();
                            String rat = type == TelephonyManager.NETWORK_TYPE_LTE ? " · 4G"
                                : (type == TelephonyManager.NETWORK_TYPE_UMTS || type == TelephonyManager.NETWORK_TYPE_HSDPA
                                || type == TelephonyManager.NETWORK_TYPE_HSUPA || type == TelephonyManager.NETWORK_TYPE_HSPA
                                || type == TelephonyManager.NETWORK_TYPE_HSPAP) ? " · 3G" : L.t(" · conectat");
                            simLabels[slot] = name + rat;
                        } else if(service.getState() == ServiceState.STATE_EMERGENCY_ONLY) simLabels[slot] = L.t("doar urgențe");
                        else if(service.getState() == ServiceState.STATE_POWER_OFF) simLabels[slot] = L.t("radio oprit");
                        else simLabels[slot] = L.t("fără serviciu");
                        break;
                    }
                } catch(SecurityException ignored) { simLabels[slot] = L.t("acces neacordat"); }
                catch(RuntimeException ignored) { simLabels[slot] = L.t("indisponibil"); }
            }
        }
        boolean key(int key, int repeat) {
            if(!grid&&cardAvailable()&&key==KeyEvent.KEYCODE_STAR){if(repeat==0){cardIndex=(cardIndex+1)%cards.length;shortcut=4+cardIndex;invalidate();}return true;}
            if(!grid&&cardAvailable()&&key==KeyEvent.KEYCODE_POUND){if(repeat==0)openCard();return true;}
            if (key == KeyEvent.KEYCODE_BACK) {
                if (repeat == 0) onBackPressed();
                return true;
            }
            if (key == KeyEvent.KEYCODE_SOFT_RIGHT) {
                if (repeat == 0) {
                    if (grid) onBackPressed();
                    else open(rightApp);
                }
                return true;
            }
            if (key == KeyEvent.KEYCODE_MENU || key == KeyEvent.KEYCODE_SOFT_LEFT) {
                if (repeat == 0) {
                    if (grid) options();
                    else notifications();
                }
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                if (repeat == 0) {
                    if (grid) { int app=focusedApp(); if(app>=0)open(app); }
                    else if(shortcut>=4)openCard();
                    else if(shortcut>=0)open(shortcuts[shortcut]);
                    else { grid = true; message = ""; invalidate(); }
                }
                return true;
            }
            // Green opens the dialer only; this app never places a call.
            if (key == KeyEvent.KEYCODE_CALL) { if (repeat == 0) open(0); return true; }
            int digit = key - KeyEvent.KEYCODE_1;
            if(grid&&(key==KeyEvent.KEYCODE_STAR||key==KeyEvent.KEYCODE_POUND)){
                if(repeat==0&&!simple)category(category+(key==KeyEvent.KEYCODE_STAR?-1:1));
                return true;
            }
            if(grid && key==KeyEvent.KEYCODE_0) {
                if(order.length>9){selected=(selected/9+1)%((order.length+8)/9)*9;invalidate();}
                return true;
            }
            if (grid && digit >= 0 && digit < 9) {
                if(order.length>0){selected = Math.min(order.length-1,(selected/9)*9+digit); invalidate();}
                return true;
            }
            if(!grid&&key==KeyEvent.KEYCODE_DPAD_UP){if(repeat==0)startActivity(new Intent(MainActivity.this,QuickSettingsActivity.class));return true;}
            if(!grid&&key==KeyEvent.KEYCODE_DPAD_DOWN){if(repeat==0)notifications();return true;}
            if (!grid && (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                moveHomeFocus(key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                return true;
            }
            int delta = key == KeyEvent.KEYCODE_DPAD_UP ? -3 : key == KeyEvent.KEYCODE_DPAD_DOWN ? 3
                : key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
            if (delta != 0) {
                if(order.length==0)return true;
                if(list) {
                    if(key==KeyEvent.KEYCODE_DPAD_UP) selected=CircularNavigation.step(selected,-1,order.length);
                    if(key==KeyEvent.KEYCODE_DPAD_DOWN) selected=CircularNavigation.step(selected,1,order.length);
                    if(!simple&&key==KeyEvent.KEYCODE_DPAD_LEFT)category(category-1);
                    if(!simple&&key==KeyEvent.KEYCODE_DPAD_RIGHT)category(category+1);
                }
                else if(selected==0&&delta<0)selected=order.length-1;else if(selected==order.length-1&&delta>0)selected=0;else selected=Math.max(0,Math.min(order.length-1,selected+delta));
                message = ""; invalidate(); return true;
            }
            return false;
        }
        void open(int index) {
            if(index>=38&&index<=47){HomeAccess.record(MainActivity.this,index);Intent intent=new Intent(MainActivity.this,new Class<?>[]{FeedsActivity.class,FeedsActivity.class,StreamRadioActivity.class,SshActivity.class,LexiconActivity.class,StudyActivity.class,ZipActivity.class,ExpensesActivity.class,MetronomeActivity.class,QrActivity.class}[index-38]);if(index==39)intent.putExtra("podcast",true);startActivity(intent);return;}
            if(index==36||index==37){HomeAccess.record(MainActivity.this,index);startActivity(new Intent(MainActivity.this,index==36?PremiumStudioActivity.class:WebAppsActivity.class));return;}
            if(index==35){HomeAccess.record(MainActivity.this,index);startActivity(new Intent(MainActivity.this,GpsActivity.class));return;}
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
                message = names[index] + L.t(": indisponibil"); invalidate(); return;
            }
            try { startActivity(intent); }
            catch (RuntimeException ignored) { message = L.t("Nu se poate deschide"); invalidate(); }
        }
        final IconArt artwork=new IconArt();
        final DashboardArt dashboardArt=new DashboardArt();
        void icon(Canvas c,int index,float x,float y){artwork.draw(c,index,x,y);int count=index==0?UnreadState.calls:index==2?UnreadState.sms:0;if(showUnread&&count>0){p.setColor(palette.bar);c.drawCircle(x+20,y-20,7,p);p.setColor(0xffff577a);c.drawCircle(x+20,y-20,5,p);}}
        protected void onDraw(Canvas output) {
            long start = android.os.SystemClock.elapsedRealtimeNanos();
            super.onDraw(output);
            output.save();
            output.scale(getWidth()/240f, getHeight()/302f);
            home.draw(output,this);
            output.restore();
            UiMetrics.draw(android.os.SystemClock.elapsedRealtimeNanos()-start);
        }
    }
}
