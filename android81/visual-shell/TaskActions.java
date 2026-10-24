package ro.leo.visual;

import android.app.ActivityManager;
import android.content.*;
import android.content.pm.*;
import java.util.*;

/** Oreo task broker through the port's existing UID-restricted root service. */
final class TaskActions {
    static final class Entry {int id;String label,packageName;ComponentName component;}
    static ArrayList<Entry> recent(Context c){
        try{org.json.JSONArray rows=new org.json.JSONArray(broker(c,"list",-1));ArrayList<Entry> result=new ArrayList<>();for(int i=0;i<rows.length();i++){org.json.JSONObject row=rows.getJSONObject(i);Entry e=new Entry();e.id=row.getInt("id");e.packageName=row.getString("package");e.label=e.packageName;try{e.label=c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(e.packageName,0)).toString();}catch(PackageManager.NameNotFoundException ignored){}result.add(e);}return result;}catch(Exception unavailable){throw new IllegalStateException("Task broker unavailable",unavailable);}
    }
    static ArrayList<Entry> rawRecent(Context c){
        ArrayList<Entry> result=new ArrayList<>();
        ActivityManager manager=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
        for(ActivityManager.RecentTaskInfo task:manager.getRecentTasks(24,ActivityManager.RECENT_IGNORE_UNAVAILABLE)){
            ComponentName component=task.topActivity==null?task.baseIntent.getComponent():task.topActivity;
            if(component==null||component.getPackageName().equals("ro.leo.visual")||task.id<0)continue;
            Entry e=new Entry();e.id=task.id;e.component=component;e.packageName=component.getPackageName();e.label=e.packageName;
            try{ApplicationInfo info=c.getPackageManager().getApplicationInfo(e.packageName,0);e.label=c.getPackageManager().getApplicationLabel(info).toString();}catch(PackageManager.NameNotFoundException ignored){}
            result.add(e);
        }
        return result;
    }
    static void open(Context c,int id){((ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE)).moveTaskToFront(id,0);}
    static boolean close(Context c,int id)throws Exception{
        if(((android.telecom.TelecomManager)c.getSystemService(Context.TELECOM_SERVICE)).isInCall())throw new SecurityException("Call active");
        return "true".equals(broker(c,"close",id));
    }
    static boolean rawClose(int id)throws Exception{
        // removeTask is hidden in SDK 27, but present in the pinned Oreo framework.
        java.lang.reflect.Method service=ActivityManager.class.getDeclaredMethod("getService");service.setAccessible(true);
        Object manager=service.invoke(null);
        java.lang.reflect.Method remove=Class.forName("android.app.IActivityManager").getMethod("removeTask",int.class);
        return Boolean.TRUE.equals(remove.invoke(manager,id));
    }
    private static String broker(Context c,String action,int id)throws Exception{
        String apk=c.getApplicationInfo().sourceDir;
        if(!apk.matches("[A-Za-z0-9_./=+:-]+")||!(action.equals("list")||action.equals("close")))throw new SecurityException();
        String command="CLASSPATH="+apk+" /system/bin/app_process /system/bin ro.leo.visual.TaskBroker "+action+" "+id;
        Process process=new ProcessBuilder("/system/bin/leo-su","-c",command).start();
        try{if(!process.waitFor(8,java.util.concurrent.TimeUnit.SECONDS))throw new java.io.IOException("Timeout");java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[2048];int n;while((n=process.getInputStream().read(buffer))>0){bytes.write(buffer,0,n);if(bytes.size()>65536)throw new java.io.IOException("Too much output");}if(process.exitValue()!=0)throw new java.io.IOException("Broker rejected");return bytes.toString("UTF-8").trim();}finally{process.destroy();}
    }
}
