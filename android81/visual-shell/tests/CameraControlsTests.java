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
        out.putString("capabilities","exposure="+p[0].getMinExposureCompensation()+":"+p[0].getMaxExposureCompensation()+" step="+p[0].getExposureCompensationStep()+" zoom_max="+(p[0].isZoomSupported()?p[0].getMaxZoom():0)+" separate_video_sizes="+(p[0].getSupportedVideoSizes()==null?"none":p[0].getSupportedVideoSizes().size()));
        final boolean[] adjusted={false};final java.util.concurrent.CountDownLatch adjustedLatch=new java.util.concurrent.CountDownLatch(1);
        h.post(new Runnable(){public void run(){try{Camera d=(Camera)readCamera();Camera.Parameters before=d.getParameters();int original=before.getExposureCompensation();int test=original<before.getMaxExposureCompensation()?original+1:original-1;boolean exposure=before.getMinExposureCompensation()==before.getMaxExposureCompensation();if(!exposure){before.setExposureCompensation(test);d.setParameters(before);exposure=d.getParameters().getExposureCompensation()==test;before=d.getParameters();before.setExposureCompensation(original);d.setParameters(before);}boolean zoom=true;if(before.isZoomSupported()&&before.getMaxZoom()>0){int z=before.getZoom();before.setZoom(z==0?1:0);d.setParameters(before);zoom=d.getParameters().getZoom()==(z==0?1:0);before=d.getParameters();before.setZoom(z);d.setParameters(before);}adjusted[0]=exposure&&zoom;}finally{adjustedLatch.countDown();}}Object readCamera(){try{return get("camera");}catch(Exception e){throw new RuntimeException(e);}}});
        check(adjustedLatch.await(8,java.util.concurrent.TimeUnit.SECONDS)&&adjusted[0],"declared exposure/zoom accepted and restored");
        Set<String> before=photos();set("delaySeconds",0);set("burstCount",3);call("capture");
        for(int i=0;i<200&&(photos().size()<before.size()+3||(Boolean)get("taking")||(Integer)get("remaining")>0);i++)SystemClock.sleep(100);
        Set<String> created=photos();created.removeAll(before);check(created.size()==3,"burst saved three JPEGs");
        File dir=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),"Leo");for(String name:created){try(FileInputStream in=new FileInputStream(new File(dir,name))){check(in.read()==255&&in.read()==216,"valid JPEG marker");}}
        set("burstCount",1);set("reviewAfter",true);call("capture");
        boolean review=false;for(int i=0;i<120&&!review;i++){for(android.app.ActivityManager.AppTask task:((android.app.ActivityManager)getTargetContext().getSystemService(android.content.Context.ACTIVITY_SERVICE)).getAppTasks()){android.app.ActivityManager.RecentTaskInfo info=task.getTaskInfo();if(info.topActivity!=null&&GalleryActivity.class.getName().equals(info.topActivity.getClassName()))review=true;}if(!review)SystemClock.sleep(100);}
        check(review,"single-photo review opens Gallery");check(get("camera")==null,"review releases the camera");
        // Gallery runs outside the instrumented camera process; wait for its actual input window.
        boolean focused=false;for(int i=0;i<50&&!focused;i++){
            android.os.ParcelFileDescriptor fd=getUiAutomation(2).executeShellCommand("dumpsys window windows");
            StringBuilder dump=new StringBuilder();try(BufferedReader reader=new BufferedReader(new InputStreamReader(new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)))){String line;while((line=reader.readLine())!=null)dump.append(line).append('\n');}
            focused=java.util.regex.Pattern.compile("mCurrentFocus=.*GalleryActivity").matcher(dump).find();
            if(!focused)SystemClock.sleep(100);
        }
        if(!focused)throw new AssertionError("review input window not focused");
        long backTime=SystemClock.uptimeMillis();
        getUiAutomation(2).injectInputEvent(new KeyEvent(backTime,backTime,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK,0),true);getUiAutomation(2).injectInputEvent(new KeyEvent(backTime,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK,0),true);
        for(int i=0;i<100&&get("camera")==null;i++)SystemClock.sleep(100);check(get("camera")!=null,"camera resumes after review");
        set("reviewAfter",false);set("delaySeconds",3);set("burstCount",1);before=photos();call("capture");check((Boolean)get("countdown"),"timer armed");
        runOnMainSync(new Runnable(){public void run(){page.finish();}});SystemClock.sleep(3700);
        check(photos().equals(before),"leaving cancels delayed capture");check(get("camera")==null,"camera released");
        out.putString("result","PASS "+checks+" camera controls checks; four photos remain local");
    }catch(Throwable e){out.putString("error",android.util.Log.getStackTraceString(e));}finally{if(page!=null)runOnMainSync(new Runnable(){public void run(){page.finish();}});}
    finish(out.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,out);}
}
