package ro.leo.systemtests;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import com.android.internal.policy.LeoKeypadNavigation;
/** Focus geometry and scroll fixtures; no network, user data or audio changes. */
public final class KeypadNavigationV2Tests extends Instrumentation {
 KeypadFixtureActivity page;int count;LinearLayout root;Button first,last;ScrollView scroll;SeekBar slider;int releases;
 void check(boolean b,String text){if(!b)throw new AssertionError(text);count++;}
 boolean key(int code,int action){return LeoKeypadNavigation.navigate(root,new KeyEvent(0,0,action,code,0));}
 void tap(int code){check(key(code,KeyEvent.ACTION_DOWN),"down consumed "+code+" root="+root.getHeight()+" scroll="+(scroll==null?0:scroll.getHeight())+" child="+(scroll==null?0:scroll.getChildAt(0).getHeight()));check(key(code,KeyEvent.ACTION_UP),"up consumed "+code);}
 void onUi(final Runnable action){final Throwable[] failure={null};super.runOnMainSync(new Runnable(){public void run(){try{action.run();}catch(Throwable t){failure[0]=t;}}});if(failure[0]!=null)throw new RuntimeException(failure[0]);}
 void fresh(){root=new LinearLayout(page);root.setOrientation(1);root.setFocusableInTouchMode(true);page.setContentView(root);}
 Button button(String title){Button b=new Button(page);b.setText(title);return b;}
 public void onCreate(Bundle b){start();}
 public void onStart(){Bundle result=new Bundle();try{
 page=(KeypadFixtureActivity)startActivitySync(new Intent(getTargetContext(),KeypadFixtureActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();SystemClock.sleep(150);waitForIdleSync();
 onUi(new Runnable(){public void run(){fresh();LinearLayout row=new LinearLayout(page);first=button("Left");last=button("Right");row.addView(first,new LinearLayout.LayoutParams(0,52,1));row.addView(last,new LinearLayout.LayoutParams(0,52,1));root.addView(row);root.requestFocus();}});waitForIdleSync();SystemClock.sleep(150);waitForIdleSync();
 onUi(new Runnable(){public void run(){tap(KeyEvent.KEYCODE_DPAD_DOWN);check(first.hasFocus(),"row first");tap(KeyEvent.KEYCODE_DPAD_RIGHT);check(last.hasFocus(),"right button");tap(KeyEvent.KEYCODE_DPAD_LEFT);check(first.hasFocus(),"left button");check(!key(KeyEvent.KEYCODE_DPAD_LEFT,0),"no horizontal wrap into unrelated row");}});
 onUi(new Runnable(){public void run(){fresh();scroll=new ScrollView(page);TextView text=new TextView(page);text.setText("Read-only long local content");text.setMinHeight(1400);scroll.addView(text,new ViewGroup.LayoutParams(-1,1400));root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));root.requestFocus();}});waitForIdleSync();SystemClock.sleep(150);waitForIdleSync();
 onUi(new Runnable(){public void run(){tap(KeyEvent.KEYCODE_DPAD_DOWN);check(scroll.getScrollY()>0,"read-only page scrolls down");tap(KeyEvent.KEYCODE_DPAD_UP);check(scroll.getScrollY()==0,"read-only page scrolls up");check(!key(KeyEvent.KEYCODE_DPAD_CENTER,0),"read-only OK does not invent click");}});
 onUi(new Runnable(){public void run(){fresh();scroll=new ScrollView(page);LinearLayout list=new LinearLayout(page);list.setOrientation(1);for(int i=0;i<8;i++){Button b=button("Action "+i);list.addView(b,new LinearLayout.LayoutParams(-1,130));if(i==0)first=b;if(i==7)last=b;}scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));root.requestFocus();}});waitForIdleSync();SystemClock.sleep(150);waitForIdleSync();
 onUi(new Runnable(){public void run(){tap(KeyEvent.KEYCODE_DPAD_DOWN);check(first.hasFocus(),"first scroll action");tap(KeyEvent.KEYCODE_DPAD_UP);check(last.hasFocus(),"off-screen final control retained");check(scroll.getScrollY()>0,"wrapped control revealed");}});
 onUi(new Runnable(){public void run(){fresh();slider=new SeekBar(page){public boolean dispatchKeyEvent(KeyEvent e){if(e.getAction()==KeyEvent.ACTION_UP&&e.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT)releases++;return super.dispatchKeyEvent(e);}};slider.setMax(100);slider.setProgress(50);root.addView(slider,new LinearLayout.LayoutParams(-1,60));root.requestFocus();}});waitForIdleSync();SystemClock.sleep(150);waitForIdleSync();
 onUi(new Runnable(){public void run(){tap(KeyEvent.KEYCODE_DPAD_DOWN);int before=slider.getProgress();tap(KeyEvent.KEYCODE_DPAD_RIGHT);check(slider.getProgress()>before,"slider changes");check(releases==1,"slider receives paired release");check(!key(KeyEvent.KEYCODE_BACK,0),"Back untouched");}});
 result.putString("result","PASS "+count+" keypad geometry/scroll checks");
 }catch(Throwable t){result.putString("error",android.util.Log.getStackTraceString(t));}finally{if(page!=null)onUi(new Runnable(){public void run(){page.finish();}});}finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);}
}
