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
    private final android.content.BroadcastReceiver badges=new android.content.BroadcastReceiver(){
        public void onReceive(android.content.Context c,Intent i) {
            if (UnreadState.CHANGED.equals(i.getAction())) screen.invalidate();
            if (!screen.grid) queueRefresh();
        }
    };
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener homeChanged=new android.content.SharedPreferences.OnSharedPreferenceChangeListener(){public void onSharedPreferenceChanged(android.content.SharedPreferences p,String key){handler.removeCallbacks(appearanceRefresh);handler.post(appearanceRefresh);}};
    private int speedKey=-1;
    private final Runnable speed=new Runnable(){public void run(){int slot=speedKey-KeyEvent.KEYCODE_0;speedKey=-1;if(slot<2||slot>9)return;int app=HomeKeyActions.app(MainActivity.this,slot);if(app>=0){HomeAccess.open(MainActivity.this,app);return;}String number=getSharedPreferences("speed-dial",0).getString("number"+slot,"");if(number.isEmpty())android.widget.Toast.makeText(MainActivity.this,L.t("Tasta ")+slot+L.t(" neatribuită · Contacte → Apelare rapidă"),1).show();else startActivity(new Intent(MainActivity.this,PhoneActivity.class).putExtra("number",number));}};
    private final ArrayList<TelephonyManager> phones = new ArrayList<TelephonyManager>();
    private final ArrayList<PhoneStateListener> phoneListeners = new ArrayList<PhoneStateListener>();
    private SubscriptionManager subscriptions;
    private android.net.ConnectivityManager connectivity;
    private boolean networkWatching;
    private final android.net.ConnectivityManager.NetworkCallback networkChanges=new android.net.ConnectivityManager.NetworkCallback(){
        public void onAvailable(android.net.Network network){networkChanged();}
        public void onLost(android.net.Network network){networkChanged();}
        public void onCapabilitiesChanged(android.net.Network network,android.net.NetworkCapabilities caps){networkChanged();}
    };
    private void networkChanged(){handler.post(new Runnable(){public void run(){queueRefresh();}});}
    private final SubscriptionManager.OnSubscriptionsChangedListener subscriptionListener =
        new SubscriptionManager.OnSubscriptionsChangedListener() {
            public void onSubscriptionsChanged() { listenToPhones(); }
        };
    private final Handler handler = new Handler();
    private final Runnable appearanceRefresh=new Runnable(){public void run(){if(homeVisible&&screen!=null){screen.reloadAppearance();screen.reloadCards();}}};
    private final Runnable refresh = new Runnable() {
        public void run() { if (homeVisible && !screen.grid) screen.refresh(); }
    };
    private void queueRefresh() {
        if (!homeVisible || screen.grid) return;
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }
    private final ScreenTicker clock = new ScreenTicker(this,new ScreenTicker.Work() {
        public long refresh() {
            if (!screen.grid) screen.refresh();
            return android.os.SystemClock.elapsedRealtime()<180000&&(!screen.inService[0]||!screen.inService[1])?2000:60000-System.currentTimeMillis()%60000;
        }
    });
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setIntent(HomeAccess.sanitize(getIntent()));
        if(ShellHealth.start(this))return;
        getWindow().setStatusBarColor(Color.rgb(26, 14, 58));
        screen = new Screen();
        screen.loadMenuFocus();
        if (state != null) {
            screen.grid = state.getBoolean("grid");
            int app = state.getInt("focused_app", -1);
            int[] legacyFocus = state.getIntArray("category_app_focus");
            int legacyCategory = state.getInt("category", 0);
            if (app < 0 && legacyFocus != null && legacyCategory >= 0 && legacyCategory < legacyFocus.length) {
                app = legacyFocus[legacyCategory];
            }
            int folder = state.getInt("launcher_folder", LauncherFolders.ROOT);
            if (folder >= 0 && folder < LauncherFolders.COUNT) screen.enterFolder(folder);
            screen.restoreEntryFocus(state.getInt("focused_entry", app));
        }
        setContentView(screen);
        if(getIntent().getBooleanExtra("open_menu",false)){getIntent().removeExtra("open_menu");screen.grid=true;}
        if(getIntent().getBooleanExtra("edit_home",false)){getIntent().removeExtra("edit_home");screen.startEditor();}
        if(getIntent().hasExtra("open_app")){int id=getIntent().getIntExtra("open_app",-1);getIntent().removeExtra("open_app");if(id>=0&&id<AppCatalog.NAMES.length)screen.open(id);}
    }
    protected void onResume() {
        super.onResume();
        UnreadState.watch(this);android.content.IntentFilter filter=new android.content.IntentFilter(UnreadState.CHANGED);filter.addAction("ro.leo.visual.MUSIC_CHANGED");filter.addAction(FmService.CHANGED);filter.addAction(LeoNotificationService.CHANGED);registerReceiver(badges,filter);UnreadState.refresh(this);
        for(String name:new String[]{"appearance","weather","alarms","accessibility",LauncherFolders.PREFERENCES})getSharedPreferences(name,0).registerOnSharedPreferenceChangeListener(homeChanged);
        if(!offeredSetup&&!KeypadLockActivity.locked(this)&&!getSharedPreferences("setup",0).getBoolean("complete",false)){offeredSetup=true;startActivity(new Intent(this,SetupWizardActivity.class));}
        screen.reloadAppearance();
        if (!screen.grid) screen.reloadCards();
        homeVisible=true;loadHomeFavorites();
        if(KeypadLockActivity.locked(this)) startActivity(new Intent(this,KeypadLockActivity.class));
        subscriptions = (SubscriptionManager)getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE);
        try { subscriptions.addOnSubscriptionsChangedListener(subscriptionListener); }
        catch (RuntimeException ignored) { }
        listenToPhones();
        connectivity=(android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        try{connectivity.registerDefaultNetworkCallback(networkChanges);networkWatching=true;}catch(RuntimeException ignored){}
        clock.start();
    }
    protected void onPause() {homeVisible=false;favoriteGeneration++;
        clock.stop();handler.removeCallbacks(refresh);handler.removeCallbacks(speed);speedKey=-1;unregisterReceiver(badges);
        handler.removeCallbacks(appearanceRefresh);
        for(String name:new String[]{"appearance","weather","alarms","accessibility",LauncherFolders.PREFERENCES})getSharedPreferences(name,0).unregisterOnSharedPreferenceChangeListener(homeChanged);
        if (subscriptions != null) subscriptions.removeOnSubscriptionsChangedListener(subscriptionListener);
        stopPhoneListeners();
        if(networkWatching){try{connectivity.unregisterNetworkCallback(networkChanges);}catch(RuntimeException ignored){}networkWatching=false;}
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
                    public void onServiceStateChanged(ServiceState state) { queueRefresh(); }
                    public void onDataConnectionStateChanged(int state, int type) { queueRefresh(); }
                };
                phones.add(phone); phoneListeners.add(listener);
                phone.listen(listener, PhoneStateListener.LISTEN_SERVICE_STATE | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);
            }
        } catch (RuntimeException ignored) { }
        queueRefresh();
    }
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("grid", screen.grid);
        state.putInt("focused_app", screen.focusedApp());
        state.putInt("focused_entry", screen.focusedEntry());
        state.putInt("launcher_folder", screen.folder);
        super.onSaveInstanceState(state);
    }
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); intent=HomeAccess.sanitize(intent); setIntent(intent);
        if(intent.getBooleanExtra("open_menu",false)){intent.removeExtra("open_menu");if(screen.editing){screen.leaveEditor();return;}screen.grid=true;screen.shortcut=-1;screen.message="";screen.invalidate();return;}
        if(intent.getBooleanExtra("edit_home",false)){intent.removeExtra("edit_home");screen.startEditor();return;}
        if(intent.hasExtra("open_app")){int id=intent.getIntExtra("open_app",-1);intent.removeExtra("open_app");if(id>=0&&id<AppCatalog.NAMES.length){screen.open(id);return;}}
        if (screen.grid) screen.saveMenuFocus();
        screen.leaveFolder();
        screen.grid = false;screen.shortcut=-1; screen.message = ""; screen.refresh();screen.invalidate();
    }
    public void onBackPressed() {
        if(screen.editing){screen.leaveEditor();return;}
        if(screen.grid && screen.leaveFolder()) return;
        if (screen.grid || screen.shortcut != -1 || !screen.message.isEmpty()) {
            if (screen.grid) screen.saveMenuFocus();
            screen.grid = false;
            screen.shortcut = -1;
            screen.message = "";
            screen.refresh();
            screen.invalidate();
        }
    }
    public boolean dispatchKeyEvent(KeyEvent event){KeyDiagnostics.record("home",event);long start=android.os.SystemClock.elapsedRealtimeNanos();try{if(LeoActivity.isJackNavigationNoise(event)||navigationPace.consume(this,event))return true;return super.dispatchKeyEvent(event);}finally{if(event.getAction()==KeyEvent.ACTION_DOWN)UiMetrics.key(android.os.SystemClock.elapsedRealtimeNanos()-start);}}
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
        if (!screen.grid && screen.shortcut >= 4 && screen.cardAvailable()
                && screen.cards[screen.cardIndex].type == 3
                && (key == KeyEvent.KEYCODE_4 || key == KeyEvent.KEYCODE_5 || key == KeyEvent.KEYCODE_6)) {
            if (event.getRepeatCount() == 0) {
                if (key == KeyEvent.KEYCODE_5) screen.openCard();
                else if (screen.favorites.length > 0) {
                    screen.favoriteIndex = CircularNavigation.step(screen.favoriteIndex,
                        key == KeyEvent.KEYCODE_4 ? -1 : 1, screen.favorites.length);
                    screen.reloadCards();
                    screen.invalidate();
                }
            }
            return true;
        }
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
        items.add(L.t("Căutare locală"));
        items.add(L.t("Reordonează aplicațiile"));
        if(app>=0)items.add(L.t("Atribuie la soft dreapta"));
        final int moveAt=app>=0&&LauncherFolders.enabled(this)?items.size():-1;
        if(moveAt>=0)items.add(L.t("Mută în folder"));
        final int modeAt=!ShellMode.simple(this)?items.size():-1;
        if(modeAt>=0)items.add(LauncherFolders.enabled(this)?L.t("Meniu fără foldere"):L.t("Meniu cu foldere"));
        items.add(ContextHelp.label());
        shellDialog(L.t("Opțiuni aplicații"),items.toArray(new String[0]),new KeypadDialog.Action(){
            public void select(int at){
                if(at==0)personalize(new Runnable(){public void run(){appOptions();}});
                else if(at==1)startActivity(new Intent(MainActivity.this,GlobalSearchActivity.class));
                else if(at==2)startActivity(new Intent(MainActivity.this,PersonalizationActivity.class).putExtra("order",true));
                else if(at==3&&app>=0)assignRightApp(app);
                else if(at==moveAt)moveApp(app);
                else if(at==modeAt){
                    getSharedPreferences(LauncherFolders.PREFERENCES,0).edit()
                        .putBoolean("enabled",!LauncherFolders.enabled(MainActivity.this)).apply();
                    screen.reloadAppearance();
                }
                else ContextHelp.open(MainActivity.this, "MainActivity");
            }
        }).show();
    }
    private void moveApp(final int app){
        String[] places=new String[LauncherFolders.COUNT+1];
        places[0]=L.t("Meniul principal");
        int current=LauncherFolders.folder(this,app);
        for(int i=0;i<LauncherFolders.COUNT;i++)places[i+1]=LauncherFolders.label(LauncherFolders.FIRST+i);
        places[current+1]="✓ "+places[current+1];
        shellDialog(AppCatalog.NAMES[app],places,new KeypadDialog.Action(){
            public void select(int at){
                LauncherFolders.move(MainActivity.this,app,at-1);
                screen.reloadAppearance();
                android.widget.Toast.makeText(MainActivity.this,L.t("Aplicație mutată"),android.widget.Toast.LENGTH_SHORT).show();
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
        void editorOptions(){new KeypadDialog(MainActivity.this,L.t(HomeCanvasLayout.NAMES[editItem]),new String[]{L.t("Mărește")+" · "+homeLayout.size[editItem]+"%",L.t("Micșorează"),L.t("Rotește elementul")+" · "+homeLayout.rotation[editItem]*90+"°",L.t("Aliniere: liber / stânga / centru / dreapta"),L.t("Arată / ascunde"),L.t("Resetează elementul"),L.t("Resetează tot ecranul"),L.t("Salvează și închide"),ContextHelp.label()},new KeypadDialog.Action(){public void select(int i){if(i==0)homeLayout.size[editItem]=Math.min(150,homeLayout.size[editItem]+5);else if(i==1)homeLayout.size[editItem]=Math.max(50,homeLayout.size[editItem]-5);else if(i==2)homeLayout.rotation[editItem]=(homeLayout.rotation[editItem]+1)%4;else if(i==3)homeLayout.alignment[editItem]=(homeLayout.alignment[editItem]+1)%4;else if(i==4)homeLayout.visible[editItem]=!homeLayout.visible[editItem];else if(i==5)homeLayout.reset(editItem);else if(i==6){new KeypadDialog(MainActivity.this,L.t("Resetează tot ecranul"),new String[]{L.t("Anulează"),L.t("Resetează")},new KeypadDialog.Action(){public void select(int choice){if(choice==1)for(int item=0;item<HomeCanvasLayout.COUNT;item++)homeLayout.reset(item);invalidate();}}).show();}else if(i==7){homeLayout.save(MainActivity.this);editing=false;}else ContextHelp.open(MainActivity.this,"HomeEditor");invalidate();}}).show();}
        boolean homePart(Canvas c,int id,float l,float t,float r,float b){return homeLayout.begin(c,id,l,t,r,b,editing);}
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final String[] names = AppCatalog.NAMES;

        Bitmap wallpaper;
        long wallpaperStamp=-1,wallpaperSize=-1;int wallpaperStyle=-1;boolean highContrast;
        void reloadAppearance() {
            android.content.SharedPreferences appearance=getSharedPreferences("appearance",0);
            list=appearance.getBoolean("list",false);
            rightApp=HomePersonalization.rightApp(appearance);
            if(!editing)homeLayout=new HomeCanvasLayout(MainActivity.this);
            compactClock=appearance.getBoolean("home-compact",false);showSims=appearance.getBoolean("home-sims",true);showUnread=appearance.getBoolean("home-unread",true);
            int focused = focusedEntry();
            reloadMenu(focused);
            shortcuts=AppCatalog.shortcuts(MainActivity.this);textScale=HomeStyle.scale(MainActivity.this);
            homeStyle=HomeStyle.index(MainActivity.this);
            palette=HomeStyle.palette(MainActivity.this,Appearance.palette(MainActivity.this));
            SystemChrome.apply(MainActivity.this,palette);getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(palette.background));
            java.io.File file=Appearance.wallpaper(MainActivity.this);long stamp=file.lastModified(),size=file.length();
            boolean contrast=HomeStyle.contrast(MainActivity.this);if(stamp!=wallpaperStamp||size!=wallpaperSize||highContrast!=contrast||wallpaperStyle!=homeStyle){highContrast=contrast;wallpaperStyle=homeStyle;
                if(wallpaper!=null){wallpaper.recycle();wallpaper=null;}
                if(file.isFile()&&!highContrast&&homeStyle==0)wallpaper=BitmapFactory.decodeFile(file.getPath());
                wallpaperStamp=stamp;wallpaperSize=size;
            }
            invalidate();
        }
        boolean grid;
        int rightApp=1;
        boolean list=getSharedPreferences("appearance",MODE_PRIVATE).getBoolean("list",false);
        int selected;
        int folder=LauncherFolders.ROOT;
        int[] childFocus={-1,-1,-1,-1,-1};
        int[][] folderMembers=new int[LauncherFolders.COUNT][];
        void loadMenuFocus() {
            android.content.SharedPreferences saved = getSharedPreferences("launcher-focus", MODE_PRIVATE);
            int legacyApp = saved.getInt("app-" + saved.getInt("category", 0), -1);
            for(int i=0;i<childFocus.length;i++)childFocus[i]=saved.getInt("folder-"+i,-1);
            reloadMenu(saved.getInt("entry",saved.getInt("app", legacyApp)));
        }
        void saveMenuFocus() {
            android.content.SharedPreferences.Editor edit=getSharedPreferences("launcher-focus", MODE_PRIVATE).edit();
            if(focusedApp()>=0)edit.putInt("app",focusedApp());
            edit.putInt("entry",folder>=0?LauncherFolders.FIRST+folder:focusedEntry());
            if(folder>=0){childFocus[folder]=focusedApp();edit.putInt("folder-"+folder,focusedApp());}
            edit.apply();
        }
        int focusedEntry() {
            return selected >= 0 && selected < order.length ? order[selected] : -1;
        }
        int focusedApp() {
            int entry=focusedEntry();
            return LauncherFolders.isFolder(entry)?-1:entry;
        }
        void restoreAppFocus(int app) {
            restoreEntryFocus(app);
        }
        void restoreEntryFocus(int entry) {
            if(folder==LauncherFolders.ROOT&&LauncherFolders.enabled(MainActivity.this)
                    &&entry>=0&&entry<AppCatalog.NAMES.length){
                int parent=LauncherFolders.folder(MainActivity.this,entry);
                if(parent>=0){childFocus[parent]=entry;entry=LauncherFolders.FIRST+parent;}
            }
            for (int i = 0; i < order.length; i++) {
                if (order[i] == entry) {
                    selected = i;
                    return;
                }
            }
        }
        void reloadMenu(int focused){
            for(int i=0;i<folderMembers.length;i++)folderMembers[i]=LauncherFolders.entries(MainActivity.this,i);
            if(folder>=0&&folderMembers[folder].length==0)folder=LauncherFolders.ROOT;
            order=folder>=0?folderMembers[folder]:LauncherFolders.entries(MainActivity.this,LauncherFolders.ROOT);
            selected=Math.max(0,Math.min(selected,order.length-1));
            restoreEntryFocus(focused);
        }
        void enterFolder(int target){
            if(target<0||target>=LauncherFolders.COUNT||!LauncherFolders.enabled(MainActivity.this))return;
            int[] members=LauncherFolders.entries(MainActivity.this,target);
            if(members.length==0)return;
            folder=target;order=members;selected=0;restoreEntryFocus(childFocus[target]);
            saveMenuFocus();invalidate();
        }
        boolean leaveFolder(){
            if(folder<0)return false;
            int entry=LauncherFolders.FIRST+folder;
            saveMenuFocus();folder=LauncherFolders.ROOT;selected=0;reloadMenu(entry);invalidate();return true;
        }
        void stepAppPage(int direction) {
            if (order.length <= 9) return;
            int pages = (order.length + 8) / 9;
            selected = Math.floorMod(selected / 9 + direction, pages) * 9;
            invalidate();
        }
        static final int WEATHER_FOCUS=-2;
        int shortcut=-1,homeStyle;
        HomeCards.Card weather;
        boolean weatherAvailable(){return weather!=null&&homeLayout.visible[8];}
        HomeFavorites.Entry[] favorites=new HomeFavorites.Entry[0];int favoriteIndex;HomeCards.Card[] cards=new HomeCards.Card[0];int cardIndex;
        boolean cardAvailable() { return cards.length > 0 && homeLayout.visible[5]; }

        void reloadCards() {
            boolean selectedCard = shortcut >= 4 && cards.length > 0;
            int previousType = cards.length == 0 ? -1 : cards[cardIndex].type;
            long now=System.currentTimeMillis();
            HomeCards.Card[] snapshots=HomeCards.read(MainActivity.this,now);
            weather=null;
            for(HomeCards.Card card:snapshots)if(card.type==0)weather=card;
            HomeCards.Card[] candidates = getSharedPreferences("appearance", 0).getBoolean("home-contextual", true)
                ? HomeContext.read(MainActivity.this, now, snapshots) : snapshots;
            ArrayList<HomeCards.Card> distinct=new ArrayList<HomeCards.Card>();
            for(HomeCards.Card card:candidates)if(card.type!=0||!weatherAvailable())distinct.add(card);
            HomeCards.Card[] next=distinct.toArray(new HomeCards.Card[0]);
            for (int i = 0; i < next.length; i++) {
                if (next[i].type == 3 && favorites.length > 0) {
                    HomeFavorites.Entry favorite = favorites[Math.min(favoriteIndex, favorites.length - 1)];
                    next[i] = new HomeCards.Card(3, L.t("Favorit · ") + (favoriteIndex + 1) + " / " + favorites.length,
                        favorite.name, "");
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
            if(shortcut==WEATHER_FOCUS&&!weatherAvailable())shortcut=-1;
            if (changed && !grid) invalidate();
        }

        private void moveHomeFocus(int direction) {
            ArrayList<Integer> targets=new ArrayList<Integer>();
            if(homeLayout.visible[6])for(int i=0;i<shortcuts.length;i++)targets.add(i);
            if(weatherAvailable())targets.add(WEATHER_FOCUS);
            if(cardAvailable())for(int i=0;i<cards.length;i++)targets.add(4+i);
            if(targets.isEmpty())return;
            int at=targets.indexOf(shortcut);
            shortcut=targets.get(at<0?(direction>0?0:targets.size()-1):Math.floorMod(at+direction,targets.size()));
            if (shortcut >= 4) cardIndex = shortcut - 4;
            invalidate();
        }
        void openCard(){if(!cardAvailable())return;int type=cards[cardIndex].type;if(type==5){startActivity(new Intent(MainActivity.this,NotificationsActivity.class));return;}if(type==3){if(favorites.length>0)startActivity(new Intent(MainActivity.this,ContactsActivity.class).putExtra("contact_id",favorites[favoriteIndex].id));else startActivity(new Intent(MainActivity.this,ContactsActivity.class).putExtra("favorites",true));return;}Intent i=new Intent(MainActivity.this,type==0?WeatherActivity.class:type==1?ClockActivity.class:type==4?DailyAgendaActivity.class:NowPlayingActivity.class);if(type==1)i.putExtra("tab",1);startActivity(i);}
        int[] shortcuts={0,2,6,22};
        int[] order=LauncherFolders.entries(MainActivity.this,LauncherFolders.ROOT);
        float textScale=1f;
        Appearance.Palette palette=Appearance.palette(MainActivity.this);
        boolean compactClock,showSims=true,showUnread=true;
        final SimpleDateFormat timeFormat=new SimpleDateFormat("HH:mm",Locale.getDefault());
        final SimpleDateFormat dateFormat=new SimpleDateFormat("EEEE, d MMMM",L.locale());
        String time = "", date = "", message = "", networkBanner=L.t("REȚEA · ÎN AȘTEPTARE");
        final String[] simLabels = {L.t("se verifică"), L.t("se verifică")};
        final String[] simTechnology = {"", ""};
        final boolean[] simVolte={false,false},simInternet={false,false};
        final boolean[] inService={false,false};
        int simSlots=3;
        Screen() { super(MainActivity.this); setFocusable(true); setFocusableInTouchMode(true); requestFocus(); }
        public boolean isOpaque(){return true;}
        void refresh() {
            reloadCards();
            String previousTime=time,previousDate=date,first=simLabels[0],second=simLabels[1],previousBanner=networkBanner;
            String previousFirstTechnology=simTechnology[0],previousSecondTechnology=simTechnology[1];
            int previousSlots=simSlots;
            boolean previousFirstService=inService[0],previousSecondService=inService[1];
            boolean badgesChanged=false;boolean[] oldVolte=simVolte.clone(),oldInternet=simInternet.clone();
            java.util.TimeZone zone=java.util.TimeZone.getDefault();timeFormat.setTimeZone(zone);dateFormat.setTimeZone(zone);
            Date now = new Date();
            time = timeFormat.format(now);
            date = dateFormat.format(now);
            updateSims();
            for(int slot=0;slot<2;slot++)badgesChanged|=oldVolte[slot]!=simVolte[slot]||oldInternet[slot]!=simInternet[slot];
            networkBanner=NetworkStartup.label(NetworkStartup.stage(),inService[0]||inService[1],Settings.Global.getInt(getContentResolver(),Settings.Global.AIRPLANE_MODE_ON,0)!=0,android.os.SystemClock.elapsedRealtime());
            // Telemetry is cached while the grid is visible; it has no pixels there.
            if(!grid&&(badgesChanged||!time.equals(previousTime)||!date.equals(previousDate)||!simLabels[0].equals(first)||!simLabels[1].equals(second)||!networkBanner.equals(previousBanner)||!simTechnology[0].equals(previousFirstTechnology)||!simTechnology[1].equals(previousSecondTechnology)||simSlots!=previousSlots||inService[0]!=previousFirstService||inService[1]!=previousSecondService))invalidate();
        }
        void updateSims() {
            TelephonyManager base = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
            int internetSubscription=HomeNetworkBadges.internetSubscription(MainActivity.this);
            for(int slot=0;slot<2;slot++) {
                inService[slot]=false;
                simTechnology[slot]="";
                simVolte[slot]=false;simInternet[slot]=false;
                try {
                    int sim = base.getSimState(slot);
                    int bit = 1 << slot;
                    if (sim == TelephonyManager.SIM_STATE_ABSENT) simSlots &= ~bit;
                    else if (sim != TelephonyManager.SIM_STATE_UNKNOWN) simSlots |= bit;
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
                        String name = carrier.isEmpty() ? L.t("Operator") : carrier;
                        if(service == null) simLabels[slot] = L.t("se verifică");
                        else if(service.getState() == ServiceState.STATE_IN_SERVICE) {
                            inService[slot]=true;
                            simLabels[slot] = name;
                            simTechnology[slot] = NetworkTelemetry.rat(NetworkTelemetry.servingType(phone));
                            simVolte[slot]="4G".equals(simTechnology[slot])&&ImsStatus.registered(slot);
                            simInternet[slot]=HomeNetworkBadges.internetFor(info.getSubscriptionId(),internetSubscription,internetSubscription>=0);
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
                    if (grid) {
                        int entry=focusedEntry();
                        if(LauncherFolders.isFolder(entry))enterFolder(entry-LauncherFolders.FIRST);
                        else if(entry>=0) {
                            saveMenuFocus();
                            open(entry);
                        }
                    }
                    else if(shortcut==WEATHER_FOCUS)open(26);
                    else if(shortcut>=4)openCard();
                    else if(shortcut>=0)open(shortcuts[shortcut]);
                    else { grid = true; message = ""; invalidate(); }
                }
                return true;
            }
            // Green opens the dialer only; this app never places a call.
            if (key == KeyEvent.KEYCODE_CALL) { if (repeat == 0) open(0); return true; }
            int digit = key - KeyEvent.KEYCODE_1;
            if (grid && (key == KeyEvent.KEYCODE_STAR || key == KeyEvent.KEYCODE_POUND || key == KeyEvent.KEYCODE_0)) {
                if (repeat == 0) stepAppPage(key == KeyEvent.KEYCODE_STAR ? -1 : 1);
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
                    if (key == KeyEvent.KEYCODE_DPAD_LEFT) stepAppPage(-1);
                    if (key == KeyEvent.KEYCODE_DPAD_RIGHT) stepAppPage(1);
                }
                else if(selected==0&&delta<0)selected=order.length-1;else if(selected==order.length-1&&delta>0)selected=0;else selected=Math.max(0,Math.min(order.length-1,selected+delta));
                message = ""; invalidate(); return true;
            }
            return false;
        }
        void open(int index) {
            try {
                if(!AppCatalog.launch(MainActivity.this,index))message=L.t("Aplicație indisponibilă");
            } catch(RuntimeException unavailable) { message=L.t("Nu se poate deschide"); }
            invalidate();
        }
        final IconArt artwork=new IconArt();
        final CatalogIcons catalogIcons = new CatalogIcons(getContext());
        final DashboardArt dashboardArt = new DashboardArt(getContext());
        final FolderArt folderArt = new FolderArt();
        void icon(Canvas c, int index, float x, float y) {
            if(LauncherFolders.isFolder(index)){folderArt.draw(c,this,index,x,y);return;}
            if (!catalogIcons.draw(c, index, x, y)) {
                artwork.draw(c, index, x, y);
            }
        }
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
