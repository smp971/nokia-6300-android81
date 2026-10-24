package ro.leo.visual;
import android.app.*;import android.content.*;import android.graphics.Bitmap;import android.os.*;import android.provider.Telephony;import android.view.*;import android.widget.*;import java.lang.reflect.*;import java.util.*;import java.io.*;

/** Local fixtures. No calls/SMS, camera open, personal screenshots or existing task removal. */
public final class MaturityTests extends Instrumentation {
 int checks;Activity activity;int fixtureTask=-1;
 void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
 Field field(Object target,String name)throws Exception{Class<?> c=target.getClass();while(c!=null){try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){c=c.getSuperclass();}}throw new NoSuchFieldException(name);}
 Object get(Object o,String n)throws Exception{return field(o,n).get(o);}
 void set(Object o,String n,Object v)throws Exception{field(o,n).set(o,v);}
 void call(final Object target,String name,Class<?>[] types,final Object...values)throws Exception{final Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);runOnMainSync(new Runnable(){public void run(){try{m.invoke(target,values);}catch(Exception e){throw new RuntimeException(e);}}});waitForIdleSync();}
 Activity open(Class<?> type){return open(new Intent(getTargetContext(),type));}
 Activity open(Intent intent){if(intent.getComponent()!=null&&intent.getComponent().getClassName().equals(MainActivity.class.getName())){
    getTargetContext().startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    final Activity[] found={null};for(int retry=0;retry<50&&found[0]==null;retry++){runOnMainSync(new Runnable(){public void run(){try{Class<?> thread=Class.forName("android.app.ActivityThread");Object current=thread.getDeclaredMethod("currentActivityThread").invoke(null);Field records=thread.getDeclaredField("mActivities");records.setAccessible(true);for(Object record:((Map<?,?>)records.get(current)).values()){Field item=record.getClass().getDeclaredField("activity");item.setAccessible(true);Object value=item.get(record);if(value instanceof MainActivity&&!((Activity)value).isFinishing())found[0]=(Activity)value;}}catch(Exception e){throw new RuntimeException(e);}}});if(found[0]==null)SystemClock.sleep(100);}if(found[0]==null)throw new AssertionError("Home unavailable");activity=found[0];
   }else activity=startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));runOnMainSync(new Runnable(){public void run(){activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);}});waitForIdleSync();return activity;}
 void close(){if(activity!=null){final Activity old=activity;runOnMainSync(new Runnable(){public void run(){old.finish();}});activity=null;waitForIdleSync();}}
 void capture(String name)throws Exception{waitForIdleSync();SystemClock.sleep(400);Bitmap b=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"maturity-"+name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}}
 public void onCreate(Bundle b){start();}
 public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();try{
  int[] all=AppCatalog.menuOrder(c,0);Set<Integer> visible=new HashSet<Integer>();for(int id:all){check(!AppCatalog.settingsOnly(id),"settings-only entry omitted");check(visible.add(id),"no repeated IDs");}check(all.length==22&&AppCatalog.NAMES.length==35,"22 launcher entries; 35 stable IDs retained");
  Set<Integer> grouped=new HashSet<Integer>();for(int group=1;group<AppCatalog.CATEGORIES.length;group++){int[] ids=AppCatalog.menuOrder(c,group);check(ids.length>0,"nonempty category");for(int id:ids)check(grouped.add(id)&&AppCatalog.category(id)==group,"category membership unique");}check(grouped.equals(visible),"categories cover visible launcher exactly");
  Activity home=open(MainActivity.class);Object screen=get(home,"screen");call(screen,"category",new Class<?>[]{int.class},5);check(((int[])get(screen,"order")).length==3,"small system category");set(screen,"grid",true);call(screen,"key",new Class<?>[]{int.class,int.class},KeyEvent.KEYCODE_9,0);check((Integer)get(screen,"selected")==2,"digit selection clamped in short category");call(screen,"category",new Class<?>[]{int.class},2);capture("media-menu");close();
  check(SmsFolders.accepts(2,Telephony.Sms.MESSAGE_TYPE_SENT)&&!SmsFolders.accepts(2,Telephony.Sms.MESSAGE_TYPE_FAILED),"sent folder separates failed messages");check(SmsFolders.accepts(3,Telephony.Sms.MESSAGE_TYPE_QUEUED)&&SmsFolders.accepts(3,Telephony.Sms.MESSAGE_TYPE_OUTBOX),"pending folder includes queued and outbox");check(SmsFolders.accepts(0,Telephony.Sms.MESSAGE_TYPE_FAILED)&&SmsFolders.accepts(4,Telephony.Sms.MESSAGE_TYPE_FAILED),"all and failed folders retain failures");
  final MessagesActivity messages=(MessagesActivity)open(new Intent(c,MessagesActivity.class).putExtra("preview",true));
  Class<?> message=Class.forName("ro.leo.visual.MessagesActivity$Message");Constructor<?> constructor=message.getDeclaredConstructor();constructor.setAccessible(true);final ArrayList<Object> rows=new ArrayList<Object>();
  for(int i=0;i<3;i++){Object m=constructor.newInstance();set(m,"address","Exemplu Leo");set(m,"body",i==0?"Text primit":i==1?"Text trimis":"Text eșuat");set(m,"type",i==0?1:i==1?2:5);set(m,"date",System.currentTimeMillis()+i);rows.add(m);}
  set(messages,"folder",2);call(messages,"display",new Class<?>[]{String.class,ArrayList.class},"",rows);List<?> labels=(List<?>)get(messages,"listRows");check(labels.size()==1&&labels.get(0).toString().contains("Text trimis"),"sent message visible in sent folder");
  set(messages,"folder",4);call(messages,"display",new Class<?>[]{String.class,ArrayList.class},"",rows);check(((List<?>)get(messages,"listRows")).get(0).toString().contains("Text eșuat"),"failed message visible separately");close();
  Activity wizard=open(SetupWizardActivity.class);set(wizard,"step",8);call(wizard,"show",new Class<?>[]{});check(((TextView)((LeoActivity)wizard).root.getChildAt(0)).getText().toString().contains("9/10"),"expanded setup has ten pages");capture("setup-keys");close();
  check("Lanternă".equals(CameraActivity.flashLabel("torch"))&&"Auto".equals(CameraActivity.flashLabel("auto")),"camera flash labels");
  // Create a separate Android Settings task solely for the task-control test.
  Set<Integer> before=new HashSet<Integer>();for(TaskActions.Entry task:TaskActions.recent(c))before.add(task.id);
  c.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
  for(int retry=0;retry<6&&fixtureTask<0;retry++){SystemClock.sleep(150);for(TaskActions.Entry task:TaskActions.recent(c))if(task.packageName.equals("com.android.settings")&&!before.contains(task.id)){fixtureTask=task.id;break;}}
  check(fixtureTask>=0,"new external Settings task discovered");TaskActions.open(c,fixtureTask);check(TaskActions.close(c,fixtureTask),"fixture task removed through Oreo task API");int removed=fixtureTask;fixtureTask=-1;boolean exists=false;for(TaskActions.Entry task:TaskActions.recent(c))exists|=task.id==removed;check(!exists,"removed task absent; existing tasks untouched");
  open(RecentAppsActivity.class);for(int retry=0;retry<70&&get(activity,"listRows")==null;retry++)SystemClock.sleep(150);check(((List<?>)get(activity,"listRows")).get(0).toString().startsWith("Acasă Leo"),"recents offers shell return");close();
  result.putString("result","PASS "+checks+" maturity checks; local SMS fixtures and one disposable Settings task");
 }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));}finally{close();if(fixtureTask>=0)try{TaskActions.close(c,fixtureTask);}catch(Exception e){result.putString("cleanup_error",e.toString());}}
 finish(result.containsKey("error")||result.containsKey("cleanup_error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);}
}
