package ro.leo.visual;
import android.app.*;import android.content.*;import android.content.pm.*;import android.os.*;import android.view.*;import org.json.*;import java.io.*;import java.util.*;
/** Local fixtures only: never sends a message, places a call or changes personal contacts. */
public final class DailyUse08Tests extends Instrumentation {
 int checks;Activity page;Context fixture;String prefix="comfort-"+UUID.randomUUID()+"-";final ArrayList<String> prefs=new ArrayList<String>();
 void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
 Activity open(Class<?> type){final Activity a=startActivitySync(new Intent(getTargetContext(),type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));page=a;runOnMainSync(new Runnable(){public void run(){a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);}});return a;}
 void close(){if(page!=null){final Activity old=page;runOnMainSync(new Runnable(){public void run(){old.finish();}});page=null;waitForIdleSync();}}
 void delete(File f){File[] children=f.listFiles();if(children!=null)for(File child:children)delete(child);f.delete();}
 void appCommand(final String command)throws Exception{final java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);runOnMainSync(new Runnable(){public void run(){try{java.lang.reflect.Method m=AppControlActivity.class.getDeclaredMethod("command",String.class,Runnable.class);m.setAccessible(true);m.invoke(page,command,new Runnable(){public void run(){done.countDown();}});}catch(Exception e){throw new RuntimeException(e);}}});if(!done.await(15,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("app command did not finish");}
 public void onCreate(Bundle b){start();}
 public void onStart(){Bundle out=new Bundle();final Context real=getTargetContext();final File dir=new File(real.getCacheDir(),prefix);dir.mkdir();fixture=new ContextWrapper(real){public SharedPreferences getSharedPreferences(String name,int mode){if(!prefs.contains(name))prefs.add(name);return real.getSharedPreferences(prefix+name,mode);}public File getFilesDir(){return dir;}public Context getApplicationContext(){return this;}};
 try{
  check(ContactDuplicatesActivity.key("00 40 721 123 456").equals("+40721123456"),"international exact duplicate normalization");
  check(!ContactDuplicatesActivity.key("0721123456").equals(ContactDuplicatesActivity.key("0721123457")),"different numbers never collapse");
  check(ConversationPrefs.key("Info-Bank").equals("info-bank"),"alphanumeric sender preserved");
  check(!ConversationPrefs.key("BANK1").equals(ConversationPrefs.key("1")),"named sender not reduced to digits");
  ConversationPrefs.toggle(fixture,"pinned","+40 721 123 456");check(ConversationPrefs.has(fixture,"pinned","0040721123456"),"pin survives number formatting");
  ConversationPrefs.toggle(fixture,"archived","+40721123456");check(!ConversationPrefs.visible(fixture,0,"+40721123456")&&ConversationPrefs.visible(fixture,1,"+40721123456"),"archive hides main and retains archive access");
  ConversationPrefs.toggle(fixture,"blocked","+40721123456");check(ConversationPrefs.visible(fixture,2,"+40721123456")&&!ConversationPrefs.visible(fixture,1,"+40721123456"),"filter has separate reversible folder");
  ConversationPrefs.toggle(fixture,"blocked","+40721123456");check(ConversationPrefs.visible(fixture,1,"+40721123456"),"unfilter preserves archival state");
  ConversationPrefs.toggle(fixture,"archived","+40721123456");check(ConversationPrefs.visible(fixture,0,"+40721123456"),"unarchive restores main");
  ArrayList<String> links=MessageLinks.extract("Visit https://example.org/help and email demo@example.org");check(links.contains("https://example.org/help")&&links.contains("mailto:demo@example.org"),"safe web and email extraction");
  check(MessageLinks.extract("javascript:alert(1)").isEmpty(),"script URL not offered");
  int[] order=HomeCards.order("4,4,0,99,-1");check(order.length==5&&order[0]==4&&order[1]==0,"agenda card order deduplicated");
  HashSet<Integer> unique=new HashSet<Integer>();for(int id:order)unique.add(id);check(unique.size()==5,"all cards preserved once");
  TrackBookmarks.save(fixture,"file:///podcast.mp3",23456,900000);check(TrackBookmarks.position(fixture,"file:///podcast.mp3")==23456,"long-form track progress");
  TrackBookmarks.save(fixture,"file:///short.mp3",1000,120000);check(TrackBookmarks.position(fixture,"file:///short.mp3")==0,"short track not auto bookmarked");
  TrackBookmarks.save(fixture,"file:///podcast.mp3",899000,900000);check(TrackBookmarks.position(fixture,"file:///podcast.mp3")==0,"completed episode resets bookmark");
  for(int n=0;n<205;n++)TrackBookmarks.save(fixture,"file:///episode-"+n,1234,900000);
  check(fixture.getSharedPreferences("track-progress",0).getAll().size()<=400,"per-track storage bounded to 200");
  SharedPreferences appearance=fixture.getSharedPreferences("appearance",0);appearance.edit().putInt("theme",10).putInt("accent",-1).putBoolean("warm",true).putString("app-order","preserve").putInt("shortcut0",3).commit();
  VisualProfilesActivity.save(fixture,0,"Night fixture");appearance.edit().putInt("theme",11).putBoolean("warm",false).commit();VisualProfilesActivity.apply(fixture,0);
  check(appearance.getInt("theme",0)==10&&appearance.getBoolean("warm",false),"visual profile applies saved colors and rendering preference");
  check(appearance.getString("app-order","").equals("preserve")&&appearance.getInt("shortcut0",0)==3,"profile preserves app order and Home shortcuts");
  ThemeCheckpoint.restore(fixture);check(appearance.getInt("theme",0)==11&&!appearance.getBoolean("warm",true),"visual profile has usable undo checkpoint");
  JSONObject bad=VisualProfilesActivity.read(fixture,0);bad.getJSONObject("settings").put("theme",999);BackupEngine.write(new File(VisualProfilesActivity.folder(fixture,0),"profile.json"),bad.toString().getBytes("UTF-8"));boolean rejected=false;try{VisualProfilesActivity.apply(fixture,0);}catch(Exception e){rejected=true;}check(rejected&&appearance.getInt("theme",0)==11,"invalid profile rejected before changing appearance");
  PackageInfo p=new PackageInfo();p.packageName="org.example.leo.fixture";p.applicationInfo=new ApplicationInfo();check(AppControlActivity.removable(p,null),"user app can be offered removal");p.applicationInfo.flags=ApplicationInfo.FLAG_SYSTEM;check(!AppControlActivity.removable(p,null),"system removal blocked");p.applicationInfo.flags=0;p.packageName="ro.leo.visual";check(!AppControlActivity.removable(p,null),"Leo removal blocked");p.packageName="org.example.leo.fixture";check(!AppControlActivity.removable(p,"org.example.leo.fixture/.Ime"),"active keyboard removal blocked");
  check(BackupEngine.category("sms-organization").equals("settings")&&BackupEngine.allowed("sms-organization","blocked","set"),"organization in selective settings backup");
  open(VisualProfilesActivity.class);check(((LeoActivity)page).root!=null,"visual profile screen opens from keys");close();
  open(AppControlActivity.class);android.widget.ListView installed=null;for(int n=0;n<120&&installed==null;n++){waitForIdleSync();android.widget.LinearLayout root=((LeoActivity)page).root;if(root!=null)for(int k=0;k<root.getChildCount();k++)if(root.getChildAt(k) instanceof android.widget.ListView)installed=(android.widget.ListView)root.getChildAt(k);if(installed==null)SystemClock.sleep(100);}check(installed!=null&&installed.getCount()>0,"app center finishes reading installed apps");
  final String fixturePackage="ro.leo.visual.tests",permission="android.permission.CAMERA";final boolean original=real.getPackageManager().checkPermission(permission,fixturePackage)==PackageManager.PERMISSION_GRANTED;
  try{appCommand("pm grant --user 0 "+fixturePackage+" "+permission);check(real.getPackageManager().checkPermission(permission,fixturePackage)==PackageManager.PERMISSION_GRANTED,"app center grants fixture permission");appCommand("pm revoke --user 0 "+fixturePackage+" "+permission);check(real.getPackageManager().checkPermission(permission,fixturePackage)!=PackageManager.PERMISSION_GRANTED,"app center revokes fixture permission");}finally{appCommand("pm "+(original?"grant":"revoke")+" --user 0 "+fixturePackage+" "+permission);}close();
  check(new MusicEqualizer().presets(0).length==0,"equalizer safely rejects an inactive session");
  open(ContactDuplicatesActivity.class);check(((LeoActivity)page).root!=null,"duplicate review screen opens without modifying contacts");close();
  open(HomeLayoutActivity.class);check(((LeoActivity)page).root!=null,"five-card layout screen opens");close();
  open(PersonalizationActivity.class);check(((LeoActivity)page).root!=null,"personalization entry points open");close();
  out.putString("result","PASS "+checks+" comfort checks; personal data unchanged");
 }catch(Throwable e){out.putString("error",android.util.Log.getStackTraceString(e));}finally{close();for(String name:prefs)real.deleteSharedPreferences(prefix+name);delete(dir);}
 finish(out.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,out);}
}
