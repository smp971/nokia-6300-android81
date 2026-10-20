package ro.leo.visual;
import android.app.*;import android.content.Context;import android.os.*;
/** One short API pulse. Requires an announced physical test; no periodic task. */
public final class VibrationApiTest extends Instrumentation {
 public void onCreate(Bundle b){start();}
 public void onStart(){Bundle out=new Bundle();Vibrator v=(Vibrator)getTargetContext().getSystemService(Context.VIBRATOR_SERVICE);try{if(!v.hasVibrator())throw new AssertionError("No vibrator exposed");v.vibrate(VibrationEffect.createOneShot(300,VibrationEffect.DEFAULT_AMPLITUDE));SystemClock.sleep(600);out.putString("result","PASS: Android API exposes vibrator and accepted 300 ms pulse; confirm hardware via HAL log");}catch(Throwable e){out.putString("error",android.util.Log.getStackTraceString(e));}finally{v.cancel();}finish(out.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,out);}
}
