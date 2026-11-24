package ro.leo.visual;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;

/** Synthetic preview only: no provider writes, transmissions, real thread reads or draft saves. */
public final class MessagesUiTests extends Instrumentation {
    private MessagesActivity page;
    private int checks;
    private PowerManager.WakeLock awake;
    private File shots;
    private final Map<String,Map<String,?>> saved=new LinkedHashMap<String,Map<String,?>>();
    private Map<String,String> privateBefore;
    private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    private Object field(Object object,String name){try{for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}catch(Exception e){throw new RuntimeException(e);}}
    private void invoke(String method){try{Method m=MessagesActivity.class.getDeclaredMethod(method);m.setAccessible(true);m.invoke(page);}catch(Exception e){throw new RuntimeException(e);}}
    private void ui(final Runnable action){final Throwable[] failure={null};runOnMainSync(new Runnable(){public void run(){try{action.run();}catch(Throwable e){failure[0]=e;}}});if(failure[0]!=null)throw new RuntimeException(failure[0]);waitForIdleSync();}
    private void press(final int code){
        ui(new Runnable(){public void run(){
            long now=SystemClock.uptimeMillis();KeyEvent down=new KeyEvent(now,now,0,code,0),up=new KeyEvent(now,now+30,1,code,0);
            KeypadDialog dialog=KeypadDialog.current();
            if(dialog!=null&&dialog.isShowing()){dialog.dispatchKeyEvent(down);dialog.dispatchKeyEvent(up);}
            else{page.dispatchKeyEvent(down);page.dispatchKeyEvent(up);}
        }});
    }
    private void open(boolean empty){page=(MessagesActivity)startActivitySync(new Intent(getTargetContext(),MessagesActivity.class).putExtra("preview",true).putExtra("preview_overview",true).putExtra("preview_empty",empty).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();await(0);}
    private void close(){if(page!=null){ui(new Runnable(){public void run(){page.finish();}});page=null;}}
    private void await(int target){for(int i=0;i<100;i++){waitForIdleSync();if((Integer)field(page,"tabPage")==target&&(Boolean)field(page,"overview")&&!(Boolean)field(page,"loadingMessages")){check(true,"loaded tab "+target);return;}SystemClock.sleep(50);}throw new AssertionError("Tab did not load: "+target);}
    private ListView list(){return (ListView)field(page,"viewportList");}
    private int rows(){return list().getAdapter().getCount();}
    private void shot(String name)throws Exception{SystemClock.sleep(130);Bitmap image=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();check(image!=null,"screenshot "+name);try(FileOutputStream out=new FileOutputStream(new File(shots,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
    private String digest(File file)throws Exception{if(!file.exists())return "missing";MessageDigest d=MessageDigest.getInstance("SHA-256");try(FileInputStream in=new FileInputStream(file)){byte[] data=new byte[8192];int n;while((n=in.read(data))>0)d.update(data,0,n);}return Arrays.toString(d.digest());}
    private Map<String,String> privateState()throws Exception{
        Context c=getTargetContext();Map<String,String> result=new TreeMap<String,String>();File prefs=new File(c.getApplicationInfo().dataDir,"shared_prefs");
        for(String name:new String[]{"sms-outgoing","sms-options","sms-organization","sms-draft","sms-templates"})result.put(name,digest(new File(prefs,name+".xml")));
        for(String suffix:new String[]{"","-journal","-wal"})result.put("drafts"+suffix,digest(new File(c.getDatabasePath("leo-drafts.db").getPath()+suffix)));
        return result;
    }
    private void snapshot(String name){saved.put(name,new HashMap<String,Object>(getTargetContext().getSharedPreferences(name,0).getAll()));}
    @SuppressWarnings("unchecked") private void restore(){for(Map.Entry<String,Map<String,?>> group:saved.entrySet()){SharedPreferences.Editor e=getTargetContext().getSharedPreferences(group.getKey(),0).edit().clear();for(Map.Entry<String,?> row:group.getValue().entrySet()){Object v=row.getValue();String key=row.getKey();if(v instanceof Integer)e.putInt(key,(Integer)v);else if(v instanceof Long)e.putLong(key,(Long)v);else if(v instanceof Float)e.putFloat(key,(Float)v);else if(v instanceof Boolean)e.putBoolean(key,(Boolean)v);else if(v instanceof String)e.putString(key,(String)v);else if(v instanceof Set)e.putStringSet(key,new HashSet<String>((Set<String>)v));}check(e.commit(),"restored "+group.getKey());}}
    private void navigation()throws Exception{
        open(false);check(rows()==3,"primary tab excludes archived and filtered fixtures");check(list().getSelectedView() instanceof MessageUi.Row,"native recycled conversation cards");shot("conversations");
        press(KeyEvent.KEYCODE_DPAD_DOWN);check(list().getSelectedItemPosition()==1,"D-pad selects second conversation");press(KeyEvent.KEYCODE_DPAD_CENTER);
        check(!(Boolean)field(page,"overview")&&((String)field(page,"current")).equals("demo:mihai"),"OK opens selected conversation");check(page.helpControls().contains("5"),"reply shortcut is documented in application help");shot("conversation");press(KeyEvent.KEYCODE_BACK);await(0);check(list().getSelectedItemPosition()==1,"Back restores conversation selection");
        press(KeyEvent.KEYCODE_DPAD_RIGHT);await(1);check(rows()==1,"draft tab has isolated preview fixture");shot("drafts");press(KeyEvent.KEYCODE_DPAD_CENTER);check((Boolean)field(page,"composing"),"OK continues a draft directly");
        final EditText body=(EditText)field(page,"body");final String text=body.getText().toString();final int before=body.getSelectionStart();press(KeyEvent.KEYCODE_DPAD_LEFT);check((Integer)field(page,"tabPage")==1&&body.getText().toString().equals(text)&&body.getSelectionStart()<=before,"left arrow edits text instead of switching tabs");
        ui(new Runnable(){public void run(){body.setText("Salut! Acesta este un mesaj demonstrativ.");invoke("prepare");}});check((Boolean)field(page,"composing")&&KeypadDialog.current()==null,"preview cannot advance toward SMS transmission");SystemClock.sleep(3600);shot("composer");
        press(KeyEvent.KEYCODE_BACK);await(1);check(rows()==1,"editor returns to drafts without saving preview data");press(KeyEvent.KEYCODE_DPAD_RIGHT);await(2);check(rows()==1,"archive has its own filtered thread");shot("archive");
        press(KeyEvent.KEYCODE_DPAD_CENTER);check(((String)field(page,"current")).equals("demo:curier"),"archive opens its selected thread");press(KeyEvent.KEYCODE_DPAD_CENTER);check(!(Boolean)field(page,"viewingMessages"),"OK opens full message detail");press(KeyEvent.KEYCODE_BACK);press(KeyEvent.KEYCODE_BACK);await(2);check(rows()==1,"Back returns through detail to archive");
        press(KeyEvent.KEYCODE_DPAD_RIGHT);await(0);while(list().getSelectedItemPosition()>0)press(KeyEvent.KEYCODE_DPAD_UP);press(KeyEvent.KEYCODE_DPAD_UP);check(page.getCurrentFocus()==((LinearLayout)field(page,"tabs")).getChildAt(1),"Up from first row focuses active tab");press(KeyEvent.KEYCODE_DPAD_DOWN);check(page.getCurrentFocus()==list(),"Down from tab returns to list");
        press(KeyEvent.KEYCODE_DPAD_LEFT);await(2);press(KeyEvent.KEYCODE_DPAD_RIGHT);await(0);check((Integer)field(page,"organization")==0,"tab wrap returns to primary organization");
        press(KeyEvent.KEYCODE_MENU);check(KeypadDialog.current()!=null,"softkey opens real inbox options");for(int n=0;n<5;n++)press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_DPAD_CENTER);for(int n=0;n<2;n++)press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_DPAD_CENTER);await(0);check((Integer)field(page,"folder")==2&&rows()==2,"Sent filter retains existing message-type semantics");
        close();
    }
    private void themes()throws Exception{
        open(false);
        for(int theme=0;theme<Appearance.COLORS.length;theme++)for(int scale=0;scale<3;scale++){
            final int chosen=theme,size=scale;ui(new Runnable(){public void run(){getTargetContext().getSharedPreferences("appearance",0).edit().putInt("theme",chosen).commit();getTargetContext().getSharedPreferences("accessibility",0).edit().putInt("text",size).commit();page.onResume();}});
            LinearLayout tabs=(LinearLayout)field(page,"tabs");TextView center=(TextView)tabs.getChildAt(1);
            check(center.getLayout()!=null&&center.getLayout().getEllipsisCount(0)==0,"active tab label fits "+theme+"/"+scale);
            check(rows()==3&&list().getSelectedView() instanceof MessageUi.Row,"rows survive live palette/text change "+theme+"/"+scale);
        }
        ui(new Runnable(){public void run(){getTargetContext().getSharedPreferences("appearance",0).edit().putInt("theme",17).commit();getTargetContext().getSharedPreferences("accessibility",0).edit().putInt("text",2).commit();page.onResume();}});shot("conversations-large");
        press(KeyEvent.KEYCODE_DPAD_CENTER);shot("conversation-large");press(KeyEvent.KEYCODE_5);final EditText original=(EditText)field(page,"body");ui(new Runnable(){public void run(){original.setText("Text păstrat la schimbarea temei");getTargetContext().getSharedPreferences("appearance",0).edit().putInt("theme",5).commit();page.onResume();}});check(field(page,"body")==original&&original.getText().toString().equals("Text păstrat la schimbarea temei"),"live theme retains unsaved editor text and focus");close();
    }
    private void empty()throws Exception{
        ui(new Runnable(){public void run(){getTargetContext().getSharedPreferences("appearance",0).edit().putInt("theme",17).commit();getTargetContext().getSharedPreferences("accessibility",0).edit().putInt("text",0).commit();}});
        open(true);for(int target=0;target<3;target++){if(target>0){press(KeyEvent.KEYCODE_DPAD_RIGHT);await(target);}check(rows()==0,"empty tab "+target);check(page.getCurrentFocus()==((LinearLayout)field(page,"tabs")).getChildAt(1),"empty state remains navigable "+target);shot("empty-"+target);}close();
    }
    public void onCreate(Bundle args){start();}
    public void onStart(){Bundle result=new Bundle();try{
        getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);privateBefore=privateState();snapshot("appearance");snapshot("accessibility");
        awake=((PowerManager)getTargetContext().getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"keyshell:messages-ui-checks");awake.acquire(240000);
        ui(new Runnable(){public void run(){getTargetContext().getSharedPreferences("appearance",0).edit().putInt("theme",17).commit();getTargetContext().getSharedPreferences("accessibility",0).edit().putInt("text",0).putBoolean("contrast",false).commit();}});
        shots=new File(getTargetContext().getFilesDir(),"messages-ui-shots");shots.mkdirs();navigation();themes();empty();check(privateBefore.equals(privateState()),"SMS preferences and private draft database bytes unchanged");result.putString("result","PASS "+checks+" Messages UI checks; preview only, no SMS sent");
    }catch(Throwable error){result.putString("error","after "+checks+" checks: "+android.util.Log.getStackTraceString(error));}finally{
        try{close();restore();if(awake!=null&&awake.isHeld())awake.release();}catch(Throwable error){result.putString("restore_error",android.util.Log.getStackTraceString(error));}
    }
    try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"messages-ui-result.txt"))){out.write(result.toString().getBytes("UTF-8"));}catch(IOException ignored){}
    finish(result.containsKey("error")||result.containsKey("restore_error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);}
}
