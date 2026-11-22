package ro.leo.visual;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Local presentation fixtures; no calls, messages, weather requests or playback. Restores all preferences. */
public final class HomeStandbyTests extends Instrumentation {
    private int checks;
    private Activity page;
    private PowerManager.WakeLock awake;
    private File shots;
    private final Map<String,Map<String,?>> saved=new LinkedHashMap<String,Map<String,?>>();
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private void ui(final Runnable task){final Throwable[] error={null};runOnMainSync(new Runnable(){public void run(){try{task.run();}catch(Throwable e){error[0]=e;}}});if(error[0]!=null)throw new RuntimeException(error[0]);}
    private Object field(Object object,String name){try{for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}catch(Exception error){throw new RuntimeException(error);}}
    private void set(Object object,String name,Object value){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}catch(Exception e){throw new RuntimeException(e);}}
    private void open(Class<?> type){page=startActivitySync(new Intent(getTargetContext(),type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();SystemClock.sleep(400);}
    private void close(){if(page!=null){final Activity old=page;ui(new Runnable(){public void run(){old.finish();}});page=null;waitForIdleSync();}}
    private void key(KeyEvent event){KeypadDialog dialog=KeypadDialog.current();if(dialog!=null&&dialog.isShowing())dialog.dispatchKeyEvent(event);else page.dispatchKeyEvent(event);}
    private void press(final int code){ui(new Runnable(){public void run(){long now=SystemClock.uptimeMillis();key(new KeyEvent(now,now,0,code,0));key(new KeyEvent(now,now+30,1,code,0));}});waitForIdleSync();}
    private void cancel(final int code){ui(new Runnable(){public void run(){long now=SystemClock.uptimeMillis();key(new KeyEvent(now,now,0,code,0));key(new KeyEvent(now,now+30,1,code,0,0,-1,0,KeyEvent.FLAG_CANCELED));}});waitForIdleSync();}
    private void snapshot(String name){saved.put(name,new HashMap<String,Object>(getTargetContext().getSharedPreferences(name,0).getAll()));}
    @SuppressWarnings("unchecked") private void restore(){for(Map.Entry<String,Map<String,?>> group:saved.entrySet()){SharedPreferences.Editor e=getTargetContext().getSharedPreferences(group.getKey(),0).edit().clear();for(Map.Entry<String,?> row:group.getValue().entrySet()){Object v=row.getValue();String k=row.getKey();if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Set)e.putStringSet(k,new HashSet<String>((Set<String>)v));}check(e.commit(),"restore "+group.getKey());}}
    private void shot(String name)throws Exception{SystemClock.sleep(250);Bitmap image=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();check(image!=null,"capture "+name);try(FileOutputStream out=new FileOutputStream(new File(shots,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
    private SharedPreferences appearance(){return getTargetContext().getSharedPreferences("appearance",0);}
    private void fixture(){
        appearance().edit().putInt(HomeStyle.KEY,0).putInt("lock-preview",0).putBoolean("lock-media",false).putBoolean("lock-info",true).putBoolean("home-weather",true).putBoolean("home-contextual",true).putBoolean("home-unread",true).putBoolean("home-sims",true).commit();
        getTargetContext().getSharedPreferences("accessibility",0).edit().putBoolean("contrast",false).putInt("text",0).commit();
        getTargetContext().getSharedPreferences(HomeCanvasLayout.PREFERENCES,0).edit().clear().putInt("7x",0).commit();
        getTargetContext().getSharedPreferences("keyguard",0).edit().putBoolean("locked",false).commit();
        getTargetContext().getSharedPreferences("setup",0).edit().putBoolean("complete",true).commit();
        getTargetContext().getSharedPreferences("weather",0).edit().putString("city","București").putString("data","{\"current\":{\"temperature_2m\":8,\"weather_code\":2}}").putLong("updated",System.currentTimeMillis()-20*60000L).commit();
    }
    private void contracts(){
        check(HomeContext.unreadLabel(0,0).isEmpty(),"zero unread omitted");
        check(HomeContext.unreadLabel(2,1).equals("2 mesaje · 1 apel pierdut"),"explicit separate unread kinds");
        check(HomeContext.unreadLabel(-1,2).equals("2 apeluri pierdute"),"negative messages omitted");
        check(HomeStyle.badge(0).isEmpty()&&HomeStyle.badge(1).equals("1")&&HomeStyle.badge(100).equals("99+"),"bounded numeric badges");
        check(WeatherFreshness.compact(100000000L,100000000L-21600000L).contains("date vechi"),"stale weather never looks current");
        HomeCards.Card old=new HomeCards.Card(0,"Meteo · București","8°C · O condiție cu un nume foarte lung","",2,1);
        check(HomeWeatherArt.detail(old,100000000L).startsWith("date vechi"),"old-data warning takes precedence over a long condition");
        HomeCards.Card missing=new HomeCards.Card(0,"Meteo · Oraș lung","Prognoză neîncărcată","",-1,0);
        check(!HomeWeatherArt.headline(missing).contains("0°")&&HomeWeatherArt.detail(missing,1).contains("neîncărcată"),"missing weather is not zero degrees");
        check(BackupEngine.allowed("appearance",HomeStyle.KEY,"int")&&!BackupEngine.allowed("appearance",HomeStyle.KEY,"boolean"),"preset backup type");
        check(BackupEngine.allowed(HomeCanvasLayout.PREFERENCES,"8x","int")&&!BackupEngine.allowed(HomeCanvasLayout.PREFERENCES,"9x","int"),"only one new stable editor item");
        HomeCanvasLayout custom=new HomeCanvasLayout(getTargetContext());custom.x[2]=13;custom.y[2]=7;custom.visible[7]=false;custom.save(getTargetContext());
        appearance().edit().putInt(HomeStyle.KEY,1).commit();HomeCanvasLayout retained=new HomeCanvasLayout(getTargetContext());
        check(retained.x[2]==13&&retained.y[2]==7&&!retained.visible[7],"preset preserves saved transforms and hidden SIM");
        getTargetContext().getSharedPreferences(HomeCanvasLayout.PREFERENCES,0).edit().clear().putInt("7x",0).commit();
    }
    private void home()throws Exception{
        open(MainActivity.class);final MainActivity.Screen screen=(MainActivity.Screen)field(page,"screen");
        final Bitmap image=Bitmap.createBitmap(240,302,Bitmap.Config.ARGB_8888);final Canvas canvas=new Canvas(image);
        try{ui(new Runnable(){public void run(){
            for(int preset=0;preset<3;preset++)for(int text=0;text<3;text++)for(int theme=0;theme<Appearance.COLORS.length;theme++){
                appearance().edit().putInt(HomeStyle.KEY,preset).putInt("theme",theme).apply();
                getTargetContext().getSharedPreferences("accessibility",0).edit().putInt("text",text).apply();
                screen.reloadAppearance();screen.reloadCards();screen.grid=false;screen.time="08:42";screen.date="luni, 23 noiembrie";
                UnreadState.sms=2;UnreadState.calls=1;screen.cards=new HomeCards.Card[]{new HomeCards.Card(5,"Notificări",HomeContext.unreadLabel(2,1),"")};screen.cardIndex=0;
                screen.home.draw(canvas,screen);RectF[] f=screen.homeLayout.frames;
                check(!RectF.intersects(f[2],f[3])&&!RectF.intersects(f[3],f[8]),"clock/date/weather separation "+preset+"/"+text+"/"+theme);
                check(f[8].bottom<f[5].top&&f[5].bottom<f[6].top&&f[6].bottom<=276,"weather/card/shortcuts above softkeys");
                check(!RectF.intersects(f[4],f[7]),"SIM labels keep separate bounds");
                check(preset!=2||screen.palette.background==Color.BLACK,"contrast preset independent of palette");
            }
            appearance().edit().putInt(HomeStyle.KEY,0).putInt("theme",17).apply();getTargetContext().getSharedPreferences("accessibility",0).edit().putInt("text",0).apply();
            screen.reloadAppearance();screen.reloadCards();
            for(HomeCards.Card card:screen.cards)check(card.type!=0,"weather is not duplicated in contextual card");
            screen.shortcut=-1;screen.invalidate();
        }});}finally{image.recycle();}
        for(int preset=0;preset<3;preset++){final int style=preset;ui(new Runnable(){public void run(){appearance().edit().putInt(HomeStyle.KEY,style).apply();screen.reloadAppearance();screen.reloadCards();UnreadState.sms=2;UnreadState.calls=1;screen.cards=new HomeCards.Card[]{new HomeCards.Card(5,"Notificări",HomeContext.unreadLabel(2,1),"")};screen.cardIndex=0;screen.invalidate();}});shot("home-"+preset);}
        getTargetContext().getSharedPreferences("accessibility",0).edit().putInt("text",2).commit();waitForIdleSync();shot("home-large");
        getTargetContext().getSharedPreferences("weather",0).edit().putLong("updated",System.currentTimeMillis()-18*3600000L).commit();waitForIdleSync();shot("home-stale");
        getTargetContext().getSharedPreferences("weather",0).edit().putString("data","").commit();waitForIdleSync();shot("home-empty-weather");
        ui(new Runnable(){public void run(){screen.reloadCards();check(screen.weather.weatherCode<0,"missing cached observation remains unknown");}});
        ui(new Runnable(){public void run(){screen.shortcut=MainActivity.Screen.WEATHER_FOCUS;screen.invalidate();}});
        ActivityMonitor weather=addMonitor(WeatherActivity.class.getName(),null,true);try{press(KeyEvent.KEYCODE_DPAD_CENTER);check(weather.getHits()==1,"OK opens compact weather");}finally{removeMonitor(weather);}
        press(KeyEvent.KEYCODE_BACK);check(screen.shortcut==-1,"Back clears weather focus");
        ui(new Runnable(){public void run(){screen.homeLayout.visible[6]=false;screen.homeLayout.visible[5]=false;screen.shortcut=-1;}});
        press(KeyEvent.KEYCODE_DPAD_RIGHT);check(screen.shortcut==MainActivity.Screen.WEATHER_FOCUS,"D-pad reaches weather when other Home elements hidden");
        ui(new Runnable(){public void run(){screen.homeLayout.visible[8]=false;screen.reloadCards();}});check(screen.shortcut==-1,"hidden weather clears focus");
        close();
    }
    private void presets()throws Exception{
        appearance().edit().putInt(HomeStyle.KEY,0).commit();open(HomeLayoutActivity.class);
        final ListView list=(ListView)field(page,"activeList");ui(new Runnable(){public void run(){list.setSelection(3);}});waitForIdleSync();press(KeyEvent.KEYCODE_DPAD_CENTER);
        check(KeypadDialog.current()!=null,"presets reachable from real Home settings row");shot("presets");press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_DPAD_CENTER);
        check(HomeStyle.index(getTargetContext())==1,"keypad applies Minimal");check(((ListView)field(page,"activeList")).getSelectedItemPosition()==3,"selection returns to preset row");check(!AccessibilityPrefs.contrast(getTargetContext()),"preset does not modify global accessibility");close();
    }
    private void lock()throws Exception{
        getTargetContext().getSharedPreferences("weather",0).edit().putString("data","{\"current\":{\"temperature_2m\":8,\"weather_code\":2}}").putLong("updated",System.currentTimeMillis()-20*60000L).commit();
        getTargetContext().getSharedPreferences("accessibility",0).edit().putInt("text",0).commit();
        for(int preset=0;preset<3;preset++){
            appearance().edit().putInt(HomeStyle.KEY,preset).commit();open(KeypadLockActivity.class);
            check(((Appearance.Palette)field(page,"palette")).background==HomeStyle.palette(getTargetContext(),Appearance.palette(getTargetContext())).background,"standby uses same preset");
            lockScene(false);
            shot("standby-"+preset);
            if(preset==2){getTargetContext().getSharedPreferences("accessibility",0).edit().putInt("text",2).commit();waitForIdleSync();lockScene(false);shot("standby-large");check(((CanvasText)field(field(field(page,"screen"),"art"),"labels")).scale()==1.3f,"standby redraws new text size without waiting for next minute");lockScene(true);shot("standby-all-info");}
            press(KeyEvent.KEYCODE_STAR);check(KeypadLockActivity.locked(getTargetContext()),"star alone keeps lock");
            cancel(KeyEvent.KEYCODE_MENU);check((Long)field(page,"armed")==0,"canceled softkey does not arm");
            press(KeyEvent.KEYCODE_MENU);check((Long)field(page,"armed")>0,"completed softkey arms");
            if(preset==0)shot("standby-unlock");
            cancel(KeyEvent.KEYCODE_STAR);check(KeypadLockActivity.locked(getTargetContext()),"canceled star does not unlock");
            press(KeyEvent.KEYCODE_MENU);press(KeyEvent.KEYCODE_STAR);check(!KeypadLockActivity.locked(getTargetContext()),"complete softkey-star unlocks");page=null;waitForIdleSync();
        }
    }
    private void lockScene(final boolean all){ui(new Runnable(){public void run(){
        Object screen=field(page,"screen");UnreadState.sms=2;UnreadState.calls=1;
        if(all){set(screen,"cards",new HomeCards.Card[]{HomeCards.lockInfo(getTargetContext(),System.currentTimeMillis())[0],new HomeCards.Card(1,"Următoarea alarmă","mar 07:00","")});set(screen,"preview",new String[]{"Notificare demonstrativă","Exemplu local"});}
        ((View)screen).invalidate();
    }});}
    public void onCreate(Bundle args){start();}
    public void onStart(){Bundle result=new Bundle();try{
        getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        awake=((PowerManager)getTargetContext().getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"keyshell:home-checks");awake.acquire(8*60000L);
        for(String name:new String[]{"appearance","accessibility","weather","keyguard","setup",HomeCanvasLayout.PREFERENCES})snapshot(name);
        shots=new File(getTargetContext().getFilesDir(),"home-standby-shots");shots.mkdirs();
        fixture();contracts();home();presets();lock();result.putString("result","PASS "+checks+" Home/standby checks");
    }catch(Throwable e){result.putString("error","after "+checks+" checks: "+android.util.Log.getStackTraceString(e));}finally{
        try{close();restore();UnreadState.refresh(getTargetContext());if(awake!=null&&awake.isHeld())awake.release();}catch(Throwable e){result.putString("restore_error",android.util.Log.getStackTraceString(e));}
    }finish(result.containsKey("error")||result.containsKey("restore_error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);}
}
