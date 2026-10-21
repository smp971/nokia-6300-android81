package ro.leo.visual;
import android.app.*;import android.content.*;import android.content.pm.*;import android.database.Cursor;import android.graphics.*;import android.net.Uri;import android.os.*;import android.provider.*;import android.provider.ContactsContract.CommonDataKinds.*;import android.view.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;import org.json.*;
/** Local fixtures only: no installer acceptance, uninstallation, audio, calls or real sharing. */
public final class BrowserNavigationTests extends Instrumentation {
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
  Context c=getTargetContext();Bundle out=new Bundle();Activity a=null;SharedPreferences prefs=c.getSharedPreferences("browser",0);boolean had=prefs.contains("font");int font=prefs.getInt("font",17);
  try{
   a=open(BrowserActivity.class);final Activity page=a;
   final android.text.Spanned fixture=android.text.Html.fromHtml("<p>Primul test</p><a href='https://example.invalid/one'>Unu</a><p>Al doilea TEST</p><a href='https://example.invalid/two'>Doi</a><a href='javascript:alert(1)'>Refuzat</a>",android.text.Html.FROM_HTML_MODE_LEGACY);
   runOnMainSync(new Runnable(){public void run(){try{set(page,"current","https://example.invalid/local");set(page,"title","Leo fixture");set(page,"document",fixture);}catch(Exception e){throw new RuntimeException(e);}}});
   call(a,"display",new Class<?>[]{});check(((List)get(a,"pageLinks")).size()==2,"only supported nonempty links are focused");
   call(a,"onKeyDown",new Class<?>[]{int.class,KeyEvent.class},KeyEvent.KEYCODE_DPAD_RIGHT,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));check(((Integer)get(a,"linkIndex"))==0,"D-pad chooses first link");
   call(a,"selectLink",new Class<?>[]{int.class},-1);check(((Integer)get(a,"linkIndex"))==1,"link navigation wraps backwards");
   TextView body=(TextView)get(a,"body");check(((android.text.Spanned)body.getText()).getSpans(0,body.length(),android.text.style.BackgroundColorSpan.class).length>0,"link focus visibly highlighted");
   call(a,"zoom",new Class<?>[]{int.class},100);check(((Integer)get(a,"font"))==26,"text zoom upper bound");call(a,"zoom",new Class<?>[]{int.class},-100);check(((Integer)get(a,"font"))==14,"text zoom lower bound");
   set(a,"search","test");call(a,"nextMatch",new Class<?>[]{});int first=(Integer)get(a,"match");call(a,"nextMatch",new Class<?>[]{});int second=(Integer)get(a,"match");check(first>=0&&second>first,"next text occurrence uses document offsets");call(a,"nextMatch",new Class<?>[]{});check(((Integer)get(a,"match"))==first,"text search wraps");
   for(int i=0;i<4;i++){set(a,"current","https://example.invalid/"+i);call(a,"rememberPage",new Class<?>[]{});}check(((ArrayDeque)get(a,"trail")).size()==3,"back stack bounded at three pages");
   set(a,"current","https://example.invalid/after");call(a,"backPage",new Class<?>[]{});check(get(a,"current").equals("https://example.invalid/3"),"back restores cached page without fetching");
   call(a,"address",new Class<?>[]{});check(((Boolean)get(a,"form")),"address editor remains a native keypad form");close(a);a=null;
   out.putString("result","PASS "+checks+" browser UI checks; fixture HTML only, no network requests");
  }catch(Throwable failure){out.putString("error",android.util.Log.getStackTraceString(failure));}
  finally{close(a);SharedPreferences.Editor edit=prefs.edit();if(had)edit.putInt("font",font);else edit.remove("font");edit.commit();}
  finish(out.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,out);
 }
}
