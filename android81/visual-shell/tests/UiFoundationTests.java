package ro.leo.visual;
import ro.leo.i18n.L;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** Local UI fixture: no camera, calls, media, user records or preference changes. */
public final class UiFoundationTests extends Instrumentation {
    private int checks;
    private LeoActivity activity;
    private ListView list;
    private View content,bar;
    private int count;
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    public void onCreate(Bundle b){start();}
    public void onStart(){Bundle result=new Bundle();try{
        check(NetworkStartup.label("preparing",false,false,24000).equals(L.t("REȚEA · PORNEȘTE MODEMUL")),"pre-registration modem stage");
        check(NetworkStartup.label("started",false,false,40000).equals(L.t("REȚEA · CAUTĂ OPERATORUL")),"started RIL does not imply registered service");
        check(NetworkStartup.label("started",true,false,40000).equals(L.t("L E O · CONECTAT")),"registration supersedes boot stage");
        check(NetworkStartup.label("started",true,true,40000).equals(L.t("MOD AVION")),"airplane mode supersedes cached registration");
        check(NetworkStartup.label("failed",false,false,40000).equals(L.t("REȚEA · PORNIRE NEREUȘITĂ")),"startup failure is explicit");
        check(!NetworkStartup.label("started",false,false,200000).equals(L.t("REȚEA · CAUTĂ OPERATORUL")),"startup banner is bounded");
        check(LeoPointerService.edgeDirection(KeyEvent.KEYCODE_DPAD_DOWN,318,320)==1,"bottom edge scrolls down");
        check(LeoPointerService.edgeDirection(KeyEvent.KEYCODE_DPAD_UP,0,320)==-1,"top edge scrolls up");
        check(LeoPointerService.edgeDirection(KeyEvent.KEYCODE_DPAD_DOWN,160,320)==0,"no scroll in middle");
        check(LeoPointerService.edgeDirection(KeyEvent.KEYCODE_DPAD_RIGHT,318,320)==0,"side movement cannot trigger vertical scroll");
        check(LeoPointerService.edgeDirection(KeyEvent.KEYCODE_DPAD_UP,318,320)==0,"moving away from edge does not scroll");
        // A fresh boot may leave Android in touch mode; this fixture tests DPAD
        // selection, so establish the same input mode as a physical key press.
        setInTouchMode(false);
        activity=(LeoActivity)startActivitySync(new Intent(getTargetContext(),HelpActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        runOnMainSync(new Runnable(){public void run(){
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            activity.list("Interfață Leo",Arrays.asList("Aspect și culori\nO temă pentru toate aplicațiile","Taste și navigare\nSus / jos pentru selectare","Text și lizibilitate\nInformații ușor de citit","Sunet și notificări\nControl din taste"),0,new LeoActivity.Choice(){public void pick(int i){}},"Opțiuni",null);
            for(int i=0;i<activity.root.getChildCount();i++)if(activity.root.getChildAt(i) instanceof ListView)list=(ListView)activity.root.getChildAt(i);
            content=activity.root;count=activity.root.getChildCount();bar=activity.root.getChildAt(count-1);
            for(int i=0;i<20;i++)activity.footer("Opțiuni","Deschide","Înapoi");
            check(activity.root==content&&activity.root.getChildCount()==count,"footer updates retain content and do not accumulate bars");
            check(activity.root.getChildAt(count-1)==bar,"footer identity retained");
            check(list!=null&&list.getCount()==4,"all fixture rows present");
        }});
        waitForIdleSync();SystemClock.sleep(500);
        runOnMainSync(new Runnable(){public void run(){
            check(list.getHeight()>=activity.dp(150),"compact chrome leaves room for content");
            check(bar.getBottom()<=activity.root.getHeight(),"softkeys remain within screen");
            check(activity.root.getChildAt(0).getHeight()==activity.dp(UiChrome.HEADER_DP),"shared compact header height");
            TextView row=(TextView)list.getChildAt(0);
            check(row.getText() instanceof android.text.Spanned,"secondary text preserves styled hierarchy");
            check(row.isSelected(),"first row has explicit focus");
        }});
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);waitForIdleSync();
        check(list.getSelectedItemPosition()==1,"physical down moves selection");
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);waitForIdleSync();
        check(list.getSelectedItemPosition()==3,"up from first wraps to last");
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);waitForIdleSync();
        check(list.getSelectedItemPosition()==0,"down from last wraps to first");
        runOnMainSync(new Runnable(){public void run(){for(int i=0;i<list.getChildCount();i++){TextView row=(TextView)list.getChildAt(i);check(row.getCurrentTextColor()==(row.isSelected()?activity.palette.ink:android.graphics.Color.WHITE),"recycled row text follows its actual selected surface");}}});
        Bitmap image=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();
        try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"ui-foundation.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
        runOnMainSync(new Runnable(){public void run(){
            activity.list("Listă goală",Collections.<String>emptyList(),0,new LeoActivity.Choice(){public void pick(int i){}},"",null);
            LinearLayout emptyBar=(LinearLayout)activity.root.getChildAt(activity.root.getChildCount()-1);
            check(((TextView)emptyBar.getChildAt(1)).length()==0,"empty lists do not advertise an unavailable open action");
        }});
        runOnMainSync(new Runnable(){public void run(){activity.finish();}});
        activity=(LeoActivity)startActivitySync(new Intent(getTargetContext(),PowerMenuActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();
        check(!activity.isFinishing(),"choosing restart only opens confirmation");
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();
        check(!activity.isFinishing(),"back cancels power confirmation without closing page");
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();
        check(activity.isFinishing(),"continue returns from power menu with keypad");
        result.putString("result","PASS "+checks+" UI foundation checks");
    }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));}
    finally{if(activity!=null)runOnMainSync(new Runnable(){public void run(){activity.finish();}});}
    finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);}
}
