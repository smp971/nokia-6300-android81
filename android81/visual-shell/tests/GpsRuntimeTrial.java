package ro.leo.visual;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import java.lang.reflect.*;
/** Explicit 30-second hardware request. Reports counts only, never coordinates. */
public final class GpsRuntimeTrial extends Instrumentation {
    private Activity page;
    Object get(String name)throws Exception{Field f=GpsActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(page);}
    public void onCreate(Bundle b){start();}
    public void onStart(){Bundle result=new Bundle();try{
        page=startActivitySync(new Intent(getTargetContext(),GpsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        runOnMainSync(new Runnable(){public void run(){page.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);try{Method m=GpsActivity.class.getDeclaredMethod("start");m.setAccessible(true);m.invoke(page);}catch(Exception e){throw new RuntimeException(e);}}});
        SystemClock.sleep(1000);
        if(!Boolean.TRUE.equals(get("running")))throw new AssertionError("GPS request did not start; check permission and location mode");
        int seen=0,used=0;boolean fix=false;
        for(int i=0;i<30;i++){SystemClock.sleep(1000);seen=Math.max(seen,(Integer)get("satellites"));used=Math.max(used,(Integer)get("used"));fix|=get("fix")!=null;}
        result.putInt("satellites_seen",seen);result.putInt("satellites_used",used);result.putBoolean("real_fix",fix);
        runOnMainSync(new Runnable(){public void run(){page.finish();}});SystemClock.sleep(300);
        if(Boolean.TRUE.equals(get("running")))throw new AssertionError("GPS requests remain active after leaving");
        result.putString("result","PASS request lifecycle; counts alone do not guarantee a position fix");
    }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));}finally{if(page!=null)runOnMainSync(new Runnable(){public void run(){page.finish();}});}
    finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);}
}
