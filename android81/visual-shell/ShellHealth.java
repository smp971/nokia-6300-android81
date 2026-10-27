package ro.leo.visual;
import android.content.*;import android.os.*;
/** Recovery entry after repeated short Home starts; never installs or rolls back silently. */
final class ShellHealth {
 private static boolean started;private static final Handler timer=new Handler(Looper.getMainLooper());
 static boolean start(final android.app.Activity c){if(started)return false;started=true;final SharedPreferences p=c.getSharedPreferences("shell-health",0);long now=System.currentTimeMillis(),last=p.getLong("start",0);int failures=!p.getBoolean("healthy",true)&&now>=last&&now-last<120000?p.getInt("failures",0)+1:0;p.edit().putLong("start",now).putBoolean("healthy",false).putInt("failures",failures).commit();if(failures>=3){c.startActivity(new Intent(c,RecoveryCenterActivity.class));c.finish();return true;}timer.postDelayed(new Runnable(){public void run(){p.edit().putBoolean("healthy",true).putInt("failures",0).putLong("verified",System.currentTimeMillis()).apply();}},2000);return false;}
 static String status(Context c){SharedPreferences p=c.getSharedPreferences("shell-health",0);return p.getBoolean("healthy",true)?"Home · healthy":"Home · observing startup";}
}
