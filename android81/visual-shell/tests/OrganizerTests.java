package ro.leo.visual;
import android.app.*;import android.content.*;import android.content.pm.*;import android.database.Cursor;import android.graphics.*;import android.net.Uri;import android.os.*;import android.provider.*;import android.provider.ContactsContract.CommonDataKinds.*;import android.view.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;import org.json.*;
/** Local fixtures only: no installer acceptance, uninstallation, audio, calls or real sharing. */
public final class OrganizerTests extends Instrumentation {
 int checks;void check(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}
 Field field(Object o,String n)throws Exception{Class<?> k=o.getClass();while(k!=null){try{Field f=k.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException e){k=k.getSuperclass();}}throw new NoSuchFieldException(n);}
 Object get(Object o,String n)throws Exception{return field(o,n).get(o);}void set(Object o,String n,Object v)throws Exception{field(o,n).set(o,v);}
 void call(final Object o,String name,Class<?>[] types,final Object...args)throws Exception{final Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);runOnMainSync(new Runnable(){public void run(){try{m.invoke(o,args);}catch(Exception e){throw new RuntimeException(e);}}});waitForIdleSync();}
 Activity open(Class<?> k){return open(new Intent(getTargetContext(),k));}Activity open(Intent i){final Activity a=startActivitySync(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));runOnMainSync(new Runnable(){public void run(){a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);}});for(int n=0;n<40&&!a.hasWindowFocus();n++)SystemClock.sleep(100);return a;}
 void close(final Activity a){if(a!=null)runOnMainSync(new Runnable(){public void run(){a.finish();}});waitForIdleSync();}
 Activity home(){final Activity[] out={null};runOnMainSync(new Runnable(){public void run(){getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}});for(int i=0;i<50&&out[0]==null;i++){runOnMainSync(new Runnable(){public void run(){try{Class<?> t=Class.forName("android.app.ActivityThread");Object thread=t.getMethod("currentActivityThread").invoke(null);Field f=t.getDeclaredField("mActivities");f.setAccessible(true);for(Object record:((Map<?,?>)f.get(thread)).values()){Field a=record.getClass().getDeclaredField("activity");a.setAccessible(true);Object page=a.get(record);if(page instanceof MainActivity&&!((Activity)page).isDestroyed())out[0]=(Activity)page;}}catch(Exception e){throw new RuntimeException(e);}}});if(out[0]==null)SystemClock.sleep(100);}if(out[0]==null)throw new AssertionError("Home unavailable");for(int i=0;i<30&&!out[0].hasWindowFocus();i++)SystemClock.sleep(100);return out[0];}
 void capture(String name)throws Exception{waitForIdleSync();SystemClock.sleep(300);Bitmap b=getUiAutomation().takeScreenshot();try(FileOutputStream f=new FileOutputStream(new File(getTargetContext().getFilesDir(),"flow-"+name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,f);}b.recycle();}
 void restore(Context c,Map<String,?> values){SharedPreferences.Editor e=c.getSharedPreferences("appearance",0).edit().clear();for(Map.Entry<String,?> pair:values.entrySet()){Object v=pair.getValue();if(v instanceof String)e.putString(pair.getKey(),(String)v);else if(v instanceof Integer)e.putInt(pair.getKey(),(Integer)v);else if(v instanceof Boolean)e.putBoolean(pair.getKey(),(Boolean)v);}e.commit();}
 public void onCreate(Bundle b){start();}
 public void onStart(){
  final Context c=getTargetContext(); Bundle result=new Bundle();Activity a=null;
  final String key="Leo fixture "+UUID.randomUUID();
  SharedPreferences errors=c.getSharedPreferences("diagnostic-errors",0),lists=c.getSharedPreferences("music-lists",0);
  final String oldErrors=errors.getString("items",null),oldLists=lists.getString("lists",null);
  File folder=new File(c.getExternalFilesDir(null),"organizer-"+UUID.randomUUID());
  long originalCalendar=CalendarArchive.calendar(c),fixtureStart=System.currentTimeMillis()/60000*60000;
  try{
   folder.mkdirs();
   JSONObject event=new JSONObject().put("title",key).put("start",fixtureStart).put("end",fixtureStart+3600000).put("duration",JSONObject.NULL).put("rule",JSONObject.NULL).put("zone",TimeZone.getDefault().getID()).put("allDay",0).put("alerts",new JSONArray());
   String ics=CalendarIcs.encode(new JSONArray().put(event));JSONArray parsed=CalendarIcs.decode(new ByteArrayInputStream(ics.getBytes("UTF-8")));
   check(parsed.length()==1&&parsed.getJSONObject(0).getString("title").equals(key),"ICS fixture round trip");
   check(CalendarIcs.append(c,parsed)==1,"additive Calendar insertion");
   check(CalendarIcs.append(c,parsed)==0,"second import omits duplicate");
   a=open(CalendarToolsActivity.class);check(((List)get(a,"listRows")).size()==5,"Calendar tools reachable");close(a);a=null;
   Bitmap image=Bitmap.createBitmap(120,160,Bitmap.Config.RGB_565);new Canvas(image).drawColor(0xff265766);File photo=new File(folder,"fixture.png");try(FileOutputStream out=new FileOutputStream(photo)){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
   a=open(new Intent(c,GalleryActivity.class).setData(Uri.fromFile(photo)));for(int n=0;n<30&&get(a,"photo")==null;n++)SystemClock.sleep(100);
   check(get(a,"photo")!=null,"gallery fixture decoded");
   call(a,"onKeyDown",new Class<?>[]{int.class,KeyEvent.class},KeyEvent.KEYCODE_9,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_9));check(((Integer)get(a,"zoom"))==2,"numeric zoom increases one step");
   call(a,"onKeyDown",new Class<?>[]{int.class,KeyEvent.class},KeyEvent.KEYCODE_7,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_7));check(((Integer)get(a,"zoom"))==1,"numeric zoom decreases one step");
   call(a,"mark",new Class<?>[]{});check(((Set)get(a,"marked")).size()==1,"gallery multi selection marks fixture");close(a);a=null;
   Uri content=LeoFileProvider.uri(c,photo);ArrayList<Uri> many=new ArrayList<Uri>();many.add(content);many.add(content);
   a=open(new Intent(c,ShareHubActivity.class).putParcelableArrayListExtra("many",many));Intent payload=(Intent)get(a,"payload");
   check(Intent.ACTION_SEND_MULTIPLE.equals(payload.getAction())&&payload.getClipData().getItemCount()==2,"multiple images carry ClipData");check((payload.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0&&(payload.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0,"selection grants read only");close(a);a=null;
   TreeMap<String,ArrayList<String[]>> saved=MusicLists.read(c);ArrayList<String[]> songs=new ArrayList<String[]>();songs.add(new String[]{content.toString(),"Leo fixture"});saved.put(key,songs);check(MusicLists.write(c,saved),"fixture playlist stored");
   a=open(new Intent(c,PlaylistEditorActivity.class).putExtra("name",key));check(((List)get(a,"rows")).size()==1,"playlist editor loads original reference");close(a);a=null;
   a=open(SoundScheduleActivity.class);check(((List)get(a,"listRows")).size()==8,"sound schedule and alarm selection visible without toggling");close(a);a=null;
   FaultLedger.record(c,"Fixture",new IOException("private text must never be recorded"));String error=FaultLedger.report(c);check(error.contains("IOException")&&!error.contains("private text"),"error report keeps type only");
   String report=DiagnosticActivity.collect(c,"SIM · fixture");check(report.contains("Leo "+c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName)&&report.contains("Servicii:"),"diagnostic includes app version and services");
   a=open(DiagnosticActivity.class);for(int n=0;n<30&&((String)get(a,"report")).isEmpty();n++)SystemClock.sleep(100);call(a,"export",new Class<?>[]{});check(((Boolean)get(a,"form")),"diagnostic export opens editable preview");close(a);a=null;
   result.putString("result","PASS "+checks+" organizer device checks; local fixtures only");
  }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));}
  finally{
   close(a);
   try{
    SharedPreferences.Editor e=errors.edit();if(oldErrors==null)e.remove("items");else e.putString("items",oldErrors);e.commit();
    e=lists.edit();if(oldLists==null)e.remove("lists");else e.putString("lists",oldLists);e.commit();
    long cal=CalendarArchive.calendar(c);if(cal>=0){c.getContentResolver().delete(CalendarContract.Events.CONTENT_URI,"calendar_id=? AND title=? AND dtstart=?",new String[]{""+cal,key,""+fixtureStart});if(originalCalendar<0)c.getContentResolver().delete(CalendarContract.Calendars.CONTENT_URI,"_id=?",new String[]{""+cal});}CalendarReminders.schedule(c);
    File[] files=folder.listFiles();if(files!=null)for(File f:files)f.delete();folder.delete();
   }catch(Exception e){result.putString("cleanup_error",e.toString());}
  }
  finish(result.containsKey("error")||result.containsKey("cleanup_error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
 }
}
