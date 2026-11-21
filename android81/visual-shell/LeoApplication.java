package ro.leo.visual;
import ro.leo.i18n.Language;
import android.app.*;import android.content.*;import android.os.*;import android.provider.Settings;import android.view.accessibility.AccessibilityManager;import java.util.*;
/** Visible-only UI timing and bounded repair of an already-enabled own key service. */
public final class LeoApplication extends Application implements Application.ActivityLifecycleCallbacks {
 private final Handler main=new Handler();private long due;static long worstDelay;private boolean visible,ownsKeys;private static long lastRepair;private static boolean repairing;
 private final Runnable repair=new Runnable(){public void run(){if(visible){recoverKeys(LeoApplication.this);repairKeys(LeoApplication.this,false);LeoNotificationService.recoverRepair(LeoApplication.this);LeoNotificationService.ensure(LeoApplication.this);}}};
 private final ScreenTicker pulse=new ScreenTicker(this,new ScreenTicker.Work(){public long refresh(){if(!visible)return 0;long now=SystemClock.uptimeMillis();if(due!=0)worstDelay=Math.max(worstDelay,Math.max(0,now-due));due=now+2000;return 2000;}},new Runnable(){public void run(){due=0;}});
 boolean ownsServices(){return ownsKeys;}
 private boolean ownsKeyService(){try{ActivityManager manager=(ActivityManager)getSystemService(ACTIVITY_SERVICE);List<ActivityManager.RunningAppProcessInfo> processes=manager.getRunningAppProcesses();if(processes!=null)for(ActivityManager.RunningAppProcessInfo process:processes)if(process.pid==android.os.Process.myPid())return getPackageName().equals(process.processName);}catch(RuntimeException unavailable){}return false;}
 public void onCreate(){super.onCreate();ownsKeys=ownsKeyService();if(ownsKeys){UpdateRecovery.startup(this);recoverKeys(this);LeoNotificationService.recoverRepair(this);registerReceiver(new android.content.BroadcastReceiver(){public void onReceive(Context c,Intent i){BatterySession.observe(c,i);}},new IntentFilter(Intent.ACTION_BATTERY_CHANGED));}BackupEngine.recoverOnStartup(this);Language.sync(this);final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler(){public void uncaughtException(Thread thread,Throwable failure){FaultLedger.record(LeoApplication.this,"Uncaught",failure);if(previous!=null)previous.uncaughtException(thread,failure);else android.os.Process.killProcess(android.os.Process.myPid());}});registerActivityLifecycleCallbacks(this);}
 static void recoverKeys(Context c){if(!repairing)restoreKeys(c);}
 private static void restoreKeys(Context c){
  final SharedPreferences p=c.getSharedPreferences("key-service-repair",0);if(!p.contains("before"))return;
  String current=Settings.Secure.getString(c.getContentResolver(),"enabled_accessibility_services");
  try{if(p.getString("without","").equals(current)){
   if(!Settings.Secure.putString(c.getContentResolver(),"enabled_accessibility_services",p.getString("before","")))return;
   if(p.getInt("enabled",0)!=0)Settings.Secure.putInt(c.getContentResolver(),"accessibility_enabled",1);
  }p.edit().clear().commit();}catch(RuntimeException denied){}
 }
 static void repairKeys(final Context context,boolean explicit){
  final Context c=context.getApplicationContext();final long now=SystemClock.elapsedRealtime();
  if(repairing||(!explicit&&now-lastRepair<60000)||(!explicit&&LeoPointerService.connected()))return;
  final String component="ro.leo.visual/ro.leo.visual.LeoPointerService";
  String configured=Settings.Secure.getString(c.getContentResolver(),"enabled_accessibility_services");
  if(configured==null||(!Arrays.asList(configured.split(":")).contains(component)&&!Arrays.asList(configured.split(":")).contains("ro.leo.visual/.LeoPointerService")))return;
  lastRepair=now;repairing=true;
  ArrayList<String> others=new ArrayList<String>(Arrays.asList(configured.split(":")));others.remove(component);others.remove("ro.leo.visual/.LeoPointerService");
  String without=android.text.TextUtils.join(":",others);SharedPreferences p=c.getSharedPreferences("key-service-repair",0);
  if(!p.edit().putString("before",configured).putString("without",without).putInt("enabled",Settings.Secure.getInt(c.getContentResolver(),"accessibility_enabled",1)).commit()){repairing=false;return;}
  try{if(!Settings.Secure.putString(c.getContentResolver(),"enabled_accessibility_services",without)){p.edit().clear().commit();repairing=false;return;}
   new Handler(Looper.getMainLooper()).postDelayed(new Runnable(){public void run(){restoreKeys(c);repairing=false;}},300);
  }catch(RuntimeException denied){p.edit().clear().commit();repairing=false;}
 }
 public void onTrimMemory(int level){super.onTrimMemory(level);UiMemory.trim(level);}
 public void onLowMemory(){super.onLowMemory();UiMemory.trim(android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE);}
 public void onActivityResumed(Activity a){visible=true;pulse.start();main.removeCallbacks(repair);/* Only the launcher process owns the accessibility service. */if(ownsKeys)main.postDelayed(repair,1500);}
 public void onActivityPaused(Activity a){visible=false;pulse.stop();main.removeCallbacks(repair);}
 public void onActivityCreated(Activity a,Bundle b){Language.sync(a);}public void onActivityStarted(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
}
