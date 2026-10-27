package ro.leo.visual;

import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;
import android.view.*;import android.widget.*;import java.io.*;import java.util.*;

/** Local UI/boot accounting fixtures. No network changes, user records or power actions. */
public final class DailyCandidateTests extends Instrumentation {
    int checks;LeoActivity page;
    void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    void capture(String name)throws Exception{
        Bitmap shot=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();
        try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),name))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}finally{shot.recycle();}
    }
    public void onCreate(Bundle args){start();}
    public void onStart(){Bundle result=new Bundle();final Context c=getTargetContext();
        final SharedPreferences fixture=c.getSharedPreferences("boot-readiness-fixture",0);
        try{
            fixture.edit().clear().commit();BootReadiness.record(fixture,"A",100,false);
            check(fixture.getLong("first_home_ms",-1)==100,"first Home milestone recorded");
            BootReadiness.record(fixture,"A",2100,true);BootReadiness.record(fixture,"A",5000,true);
            check(fixture.getLong("healthy_ms",-1)==2100,"process relaunch retains first healthy milestone");
            BootReadiness.record(fixture,"B",80,false);
            check(!fixture.contains("healthy_ms")&&fixture.getLong("first_home_ms",-1)==80,"new boot resets milestone");
            ListPositionDrawable position=new ListPositionDrawable(Appearance.palette(c),1);
            position.position(1,7);check("2/7".equals(position.value()),"list position is one-based");
            position.position(9,0);check("0/0".equals(position.value()),"empty counter cannot show phantom selection");
            Intent share=ShareHubActivity.textPayload("LOCAL PICKER FIXTURE");
            check(Intent.ACTION_SEND.equals(share.getAction())&&"text/plain".equals(share.getType()),"text sharing uses a normal Android intent");
            check(share.getData()==null&&!share.hasExtra(Intent.EXTRA_STREAM)
                    &&(share.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)==0,"text-only sharing grants no file access");
            setInTouchMode(false);
            page=(LeoActivity)startActivitySync(new Intent(c,HelpActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(new Runnable(){public void run(){
                page.list("Local UI fixture",Arrays.asList("One\nFirst item","Two\nSecond item","Three\nThird item"),0,new LeoActivity.Choice(){public void pick(int n){}},"Options",null);
                check(((TextView)page.root.getChildAt(0)).getCompoundDrawables()[2] instanceof ListPositionDrawable,"common header owns a compact counter");
                final TextView footer=(TextView)((LinearLayout)page.root.getChildAt(page.root.getChildCount()-1)).getChildAt(0);
                final int[] changes={0};footer.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int n,int after){}public void onTextChanged(CharSequence s,int a,int before,int count){changes[0]++;}public void afterTextChanged(android.text.Editable e){}});
                for(int n=0;n<25;n++)page.footer("Options","Open","Back");
                check(changes[0]==0,"unchanged footer labels produce no text-change/layout churn");
                page.footer("More","Open","Back");check(changes[0]==1,"changed footer action still updates");
            }});waitForIdleSync();sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);waitForIdleSync();
            runOnMainSync(new Runnable(){public void run(){ListPositionDrawable indicator=(ListPositionDrawable)((TextView)page.root.getChildAt(0)).getCompoundDrawables()[2];check("3/3".equals(indicator.value()),"counter follows circular selection");}});
            capture("leo12-list.png");runOnMainSync(new Runnable(){public void run(){page.finish();}});
            page=(LeoActivity)startActivitySync(new Intent(c,DailyReadinessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();check(!page.isFinishing(),"readiness page opens");capture("leo12-readiness.png");
            runOnMainSync(new Runnable(){public void run(){page.finish();}});
            page=(LeoActivity)startActivitySync(new Intent(c,ShareHubActivity.class)
                    .putExtra("text","LOCAL PICKER FIXTURE").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();check(!page.isFinishing(),"keypad text destination picker opens without sharing");capture("leo12-share.png");
            result.putString("result","PASS "+checks+" daily candidate checks");
        }catch(Throwable failure){result.putString("error",android.util.Log.getStackTraceString(failure));}
        finally{fixture.edit().clear().commit();if(page!=null)runOnMainSync(new Runnable(){public void run(){page.finish();}});}
        finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
}
