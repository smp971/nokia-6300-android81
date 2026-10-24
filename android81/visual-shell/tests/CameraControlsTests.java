package ro.leo.visual;
import android.app.*;import android.content.*;import android.os.*;import android.hardware.Camera;import android.view.*;import java.io.*;import java.lang.reflect.*;import java.util.*;
/** Opt-in real camera probe: three local JPEGs, then cancellation of a delayed shot. */
public final class CameraControlsTests extends Instrumentation {
    Activity page;int checks;
    Field field(String n)throws Exception{Field f=CameraActivity.class.getDeclaredField(n);f.setAccessible(true);return f;}
    Object get(String n)throws Exception{return field(n).get(page);}
    void set(final String n,final Object v){runOnMainSync(new Runnable(){public void run(){try{field(n).set(page,v);}catch(Exception e){throw new RuntimeException(e);}}});}
    void call(final String n){runOnMainSync(new Runnable(){public void run(){try{Method m=CameraActivity.class.getDeclaredMethod(n);m.setAccessible(true);m.invoke(page);}catch(Exception e){throw new RuntimeException(e);}}});}
    void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    Set<String> photos(){File dir=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"Leo");Set<String> names=new HashSet<String>();File[] files=dir.listFiles();if(files!=null)for(File f:files)if(f.getName().endsWith(".jpg"))names.add(f.getName());return names;}
    public void onCreate(Bundle b){start();}
    public void onStart(){Bundle out=new Bundle();try{
        page=startActivitySync(new Intent(getTargetContext(),CameraActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        runOnMainSync(new Runnable(){public void run(){page.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);}});
        for(int i=0;i<100&&get("camera")==null;i++)SystemClock.sleep(150);
        check(get("camera")!=null,"camera opened");
        final Camera.Parameters[] p={null};final Handler h=(Handler)get("cameraWork");final java.util.concurrent.CountDownLatch latch=new java.util.concurrent.CountDownLatch(1);
        h.post(new Runnable(){public void run(){try{p[0]=((Camera)readCamera()).getParameters();}finally{latch.countDown();}}Object readCamera(){try{return get("camera");}catch(Exception e){throw new RuntimeException(e);}}});
        check(latch.await(5,java.util.concurrent.TimeUnit.SECONDS)&&p[0]!=null,"parameters returned");
        out.putString("capabilities","exposure="+p[0].getMinExposureCompensation()+":"+p[0].getMaxExposureCompensation()+" step="+p[0].getExposureCompensationStep()+" separate_video_sizes="+(p[0].getSupportedVideoSizes()==null?"none":p[0].getSupportedVideoSizes().size()));
        Set<String> before=photos();set("delaySeconds",0);set("burstCount",3);call("capture");
        for(int i=0;i<200&&(photos().size()<before.size()+3||(Boolean)get("taking")||(Integer)get("remaining")>0);i++)SystemClock.sleep(100);
        Set<String> created=photos();created.removeAll(before);check(created.size()==3,"burst saved three JPEGs");
        File dir=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"Leo");for(String name:created){try(FileInputStream in=new FileInputStream(new File(dir,name))){check(in.read()==255&&in.read()==216,"valid JPEG marker");}}
        set("delaySeconds",3);set("burstCount",1);before=photos();call("capture");check((Boolean)get("countdown"),"timer armed");
        runOnMainSync(new Runnable(){public void run(){page.finish();}});SystemClock.sleep(3700);
        check(photos().equals(before),"leaving cancels delayed capture");check(get("camera")==null,"camera released");
        out.putString("result","PASS "+checks+" camera controls checks; three photos remain local");
    }catch(Throwable e){out.putString("error",android.util.Log.getStackTraceString(e));}finally{if(page!=null)runOnMainSync(new Runnable(){public void run(){page.finish();}});}
    finish(out.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,out);}
}
