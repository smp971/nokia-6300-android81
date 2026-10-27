package ro.leo.visual;
import android.content.*;import android.media.AudioManager;import android.app.NotificationManager;import android.os.*;
final class AccessibilityPrefs {
 private static long last;
 static boolean contrast(Context c){return c.getSharedPreferences("accessibility",0).getBoolean("contrast",false);}
 static float scale(Context c){return new float[]{1,1.15f,1.3f}[Math.max(0,Math.min(2,c.getSharedPreferences("accessibility",0).getInt("text",c.getSharedPreferences("appearance",0).getBoolean("large-text",false)?1:0)))];}
 static synchronized void alert(Context c,String kind){if(!c.getSharedPreferences("accessibility",0).getBoolean(kind,false)||SystemClock.elapsedRealtime()-last<3000)return;try{if(((AudioManager)c.getSystemService(Context.AUDIO_SERVICE)).getRingerMode()==AudioManager.RINGER_MODE_SILENT||((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).getCurrentInterruptionFilter()==NotificationManager.INTERRUPTION_FILTER_NONE||((android.telecom.TelecomManager)c.getSystemService(Context.TELECOM_SERVICE)).isInCall())return;Vibrator v=(Vibrator)c.getSystemService(Context.VIBRATOR_SERVICE);if(v!=null&&v.hasVibrator()){last=SystemClock.elapsedRealtime();v.vibrate(VibrationEffect.createWaveform(new long[]{0,100,100,100},-1),new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).build());}}catch(RuntimeException ignored){}}
}
