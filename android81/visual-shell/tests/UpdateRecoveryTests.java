package ro.leo.visual;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import java.io.*;
import java.util.UUID;

/** Reads real installed APK/session metadata, but uses owned journals/files and intercepts all starts. */
public final class UpdateRecoveryTests extends Instrumentation {
    private int checks;
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private final class Trial extends ContextWrapper {
        final String name="update-recovery-"+UUID.randomUUID();
        final File files;
        int services,homes;
        Trial(Context context){super(context);files=new File(context.getCacheDir(),name);files.mkdirs();}
        public File getFilesDir(){return files;}
        public SharedPreferences getSharedPreferences(String name,int mode){return getBaseContext().getSharedPreferences(this.name+"-"+name,mode);}
        public ComponentName startForegroundService(Intent intent){check(intent.getComponent().getClassName().equals(UpdateService.class.getName()),"only update service can resume");services++;return intent.getComponent();}
        public void startActivity(Intent intent){check(intent.getComponent().getPackageName().equals(getPackageName()),"return is to own Home only");homes++;}
    }
    private void pending(Trial context,int step,int session)throws Exception{
        File stage=new File(UpdateEngine.base(context),"stage-owned-trial");stage.mkdirs();
        UpdateEngine.copy(new File(context.getPackageManager().getApplicationInfo("ro.leo.visual",0).sourceDir),new File(stage,AppBundle.FILES[1]));
        check(UpdateEngine.prefs(context).edit().clear().putString("stage",stage.getName()).putBoolean("active",true).putInt("step",step).putInt("session",session).commit(),"owned pending journal saved");
    }
    private void remove(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)remove(child);file.delete();}
    public void onCreate(Bundle args){start();}
    public void onStart(){Bundle result=new Bundle();Trial trial=new Trial(getTargetContext());try{
        pending(trial,1,Integer.MAX_VALUE-1);
        UpdateRecovery.reconcile(trial,false);
        check(!UpdateEngine.prefs(trial).getBoolean("active",true)&&UpdateEngine.prefs(trial).getInt("step",0)==2,"lost self-update callback reconciles by installed APK hash");
        check(UpdateEngine.prefs(trial).getBoolean("return-home",false)&&trial.homes==0,"startup preserves legacy return flag for later replacement receiver");
        UpdateRecovery.reconcile(trial,true);
        check(trial.homes==(UpdateRecovery.ownHome(trial)?1:0),"replacement returns only if own Home is already selected");
        check(!UpdateEngine.prefs(trial).contains("return-home"),"return flag consumed");int homes=trial.homes;
        UpdateRecovery.reconcile(trial,true);check(trial.homes==homes,"replacement recovery is idempotent");
        pending(trial,1,Integer.MAX_VALUE-1);UpdateEngine.prefs(trial).edit().putBoolean("return-home",false).commit();UpdateRecovery.reconcile(trial,true);
        check(trial.homes==homes,"explicit no-return intent is preserved");
        pending(trial,1,Integer.MAX_VALUE-1);try(FileOutputStream output=new FileOutputStream(new File(UpdateEngine.folder(trial,"stage"),AppBundle.FILES[1]))){output.write(1);}
        UpdateRecovery.reconcile(trial,true);check(!UpdateEngine.prefs(trial).getBoolean("active",true)&&trial.homes==homes,"mismatched installed APK fails safely without Home launch");
        pending(trial,0,-1);UpdateRecovery.reconcile(trial,false);check(trial.services==1&&UpdateEngine.prefs(trial).getBoolean("active",false),"journal before session creation resumes update service");
        result.putString("result","PASS "+checks+" isolated update recovery checks; no install or real service started");
    }catch(Throwable failure){result.putString("error",android.util.Log.getStackTraceString(failure));}finally{getTargetContext().deleteSharedPreferences(trial.name+"-app-updates");remove(trial.files);}
    finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);}
}
