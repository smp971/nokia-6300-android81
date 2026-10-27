package ro.leo.visual;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.io.FileOutputStream;

/** Local page navigation only: does not activate network or device switches. */
public final class AndroidControlsTests extends Instrumentation {
    AndroidControlsActivity page;
    ListView choices;
    int checks;
    void check(boolean yes, String why) { if (!yes) throw new AssertionError(why); checks++; }
    ListView find(View view) {
        if (view instanceof ListView) return (ListView)view;
        if (view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int n=0;n<group.getChildCount();n++) {
                ListView list=find(group.getChildAt(n));
                if(list!=null) return list;
            }
        }
        return null;
    }
    public void onCreate(Bundle args) { start(); }
    public void onStart() {
        Bundle result=new Bundle();
        try {
            setInTouchMode(false);
            page=(AndroidControlsActivity)startActivitySync(new Intent(getTargetContext(),
                    AndroidControlsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            runOnMainSync(new Runnable(){public void run(){
                choices=find(page.root);
                check(choices!=null && choices.getCount()==4,"four distinct Android/Leo routes");
                check(choices.getSelectedItemPosition()==0,"first route selected");
            }});
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);waitForIdleSync();
            check(choices.getSelectedItemPosition()==3,"Up wraps to Leo notifications");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);waitForIdleSync();
            check(choices.getSelectedItemPosition()==0,"Down wraps to Android notifications");
            android.graphics.Bitmap image=getUiAutomation(
                    UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();
            check(image!=null,"page renders");
            try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),
                    "android-controls.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
            finally{image.recycle();}
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();
            check(page.isFinishing(),"Back returns from page");
            result.putString("result","PASS "+checks+" Android controls page checks");
        }catch(Throwable failure){result.putString("error",android.util.Log.getStackTraceString(failure));}
        finally{if(page!=null)runOnMainSync(new Runnable(){public void run(){page.finish();}});}
        finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}
