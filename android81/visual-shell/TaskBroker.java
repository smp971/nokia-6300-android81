package ro.leo.visual;
import android.app.*;import android.content.*;import org.json.*;
/** app_process entry point, invoked only through the existing Leo root service. */
public final class TaskBroker {
 public static void main(String[] args){try{run(args);}catch(Throwable error){System.err.println("Leo task broker: "+error.getClass().getSimpleName());System.exit(2);}}
 private static void run(String[] args)throws Exception{
  if(android.os.Process.myUid()!=0||args.length!=2)throw new SecurityException();
  android.os.Looper.prepareMainLooper();Class<?> thread=Class.forName("android.app.ActivityThread");java.lang.reflect.Method main=thread.getDeclaredMethod("systemMain");main.setAccessible(true);Object instance=main.invoke(null);java.lang.reflect.Method context=thread.getDeclaredMethod("getSystemContext");context.setAccessible(true);Context c=(Context)context.invoke(instance);
  if("list".equals(args[0])){JSONArray result=new JSONArray();for(TaskActions.Entry e:TaskActions.rawRecent(c)){JSONObject row=new JSONObject();row.put("id",e.id);row.put("package",e.packageName);result.put(row);}System.out.println(result);}
  else if("close".equals(args[0])){int id=Integer.parseInt(args[1]);boolean safe=false;for(TaskActions.Entry e:TaskActions.rawRecent(c))if(e.id==id&&!e.packageName.equals("com.android.systemui")&&!e.packageName.equals("com.android.phone")&&!e.packageName.equals("com.android.server.telecom"))safe=true;if(!safe)throw new SecurityException();System.out.println(TaskActions.rawClose(id));}
  else throw new IllegalArgumentException();
  System.exit(0);
 }
}
