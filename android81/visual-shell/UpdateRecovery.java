package ro.leo.visual;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;

/** Reconcile a lost PackageInstaller callback after self-replacement, without choosing a new Home. */
final class UpdateRecovery {
    static boolean ownHome(Context context){
        ResolveInfo home=context.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME),0);
        return home!=null&&home.activityInfo!=null&&context.getPackageName().equals(home.activityInfo.packageName);
    }
    static synchronized void reconcile(Context context,boolean replaced)throws Exception{
        SharedPreferences state=UpdateEngine.prefs(context);
        // Older builds did not store return-home. Their interrupted self-update is also recoverable.
        boolean legacySelf=state.getBoolean("active",false)&&state.getInt("step",-1)==1;
        if(legacySelf&&!state.contains("return-home"))state.edit().putBoolean("return-home",true).commit();
        boolean returnHome=state.getBoolean("return-home",legacySelf);
        UpdateEngine.reconcile(context);
        if(replaced&&returnHome&&!state.getBoolean("active",false)&&state.getInt("step",-1)==2){
            state.edit().remove("return-home").commit();
            if(ownHome(context))context.startActivity(HomeAccess.intent(context)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
        }
    }
    static void startup(final Context context){
        if(!UpdateEngine.prefs(context).getBoolean("active",false))return;
        new Thread(new Runnable(){public void run(){try{reconcile(context,false);}
            catch(Exception failure){FaultLedger.record(context,"Update recovery",failure);}}},"KeyShellUpdateRecovery").start();
    }
}
