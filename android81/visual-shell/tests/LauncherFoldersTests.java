package ro.leo.visual;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Isolated preference contracts and reversible UI fixtures. Never communicates or starts media. */
public final class LauncherFoldersTests extends Instrumentation {
    private int checks;
    private MainActivity home;
    private volatile Activity resumed;
    private PowerManager.WakeLock awake;
    private File shots;
    private final Map<String,Map<String,?>> saved=new LinkedHashMap<>();
    private final String token="folder-check-"+UUID.randomUUID()+"-";
    private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    private boolean contains(int[] entries,int target){for(int entry:entries)if(entry==target)return true;return false;}
    private void ui(final Runnable action){final Throwable[] error={null};runOnMainSync(new Runnable(){public void run(){try{action.run();}catch(Throwable e){error[0]=e;}}});if(error[0]!=null)throw new RuntimeException(error[0]);waitForIdleSync();}
    private MainActivity.Screen screen(){try{Field f=MainActivity.class.getDeclaredField("screen");f.setAccessible(true);return (MainActivity.Screen)f.get(home);}catch(Exception e){throw new RuntimeException(e);}}
    @Override public void callActivityOnResume(Activity page){super.callActivityOnResume(page);resumed=page;}
    private void openHome(){
        ui(new Runnable(){public void run(){getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}});
        for(int i=0;i<100&&!(resumed instanceof MainActivity);i++)SystemClock.sleep(50);
        check(resumed instanceof MainActivity,"bounded Home launch");home=(MainActivity)resumed;
        ui(new Runnable(){public void run(){screen().reloadAppearance();screen().grid=true;}});
    }
    private void press(final int code){ui(new Runnable(){public void run(){
        long now=SystemClock.uptimeMillis();KeypadDialog dialog=KeypadDialog.current();
        Activity target=dialog!=null&&dialog.isShowing()?null:resumed;
        KeyEvent down=new KeyEvent(now,now,0,code,0),up=new KeyEvent(now,now+40,1,code,0);
        if(target!=null){target.dispatchKeyEvent(down);target.dispatchKeyEvent(up);}else{dialog.dispatchKeyEvent(down);dialog.dispatchKeyEvent(up);}
    }});}
    private void select(final int entry){ui(new Runnable(){public void run(){screen().restoreEntryFocus(entry);screen().invalidate();}});check(screen().focusedEntry()==entry,"select entry "+entry);}
    private void shot(String name)throws Exception{
        SystemClock.sleep(160);Bitmap image=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();check(image!=null,"capture "+name);
        try(FileOutputStream out=new FileOutputStream(new File(shots,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
    }
    private void snapshot(String name){saved.put(name,new HashMap<String,Object>(getTargetContext().getSharedPreferences(name,0).getAll()));}
    @SuppressWarnings("unchecked") private void restore(){for(Map.Entry<String,Map<String,?>> group:saved.entrySet()){
        SharedPreferences.Editor e=getTargetContext().getSharedPreferences(group.getKey(),0).edit().clear();
        for(Map.Entry<String,?> row:group.getValue().entrySet()){String k=row.getKey();Object v=row.getValue();
            if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set)e.putStringSet(k,new HashSet<String>((Set<String>)v));
        }check(e.commit(),"restore "+group.getKey());
    }}
    private void coverage(Context c){
        Set<Integer> all=new HashSet<>();int folders=0;
        for(int entry:LauncherFolders.entries(c,-1)){
            if(LauncherFolders.isFolder(entry)){folders++;for(int app:LauncherFolders.entries(c,entry-LauncherFolders.FIRST))check(all.add(app),"no duplicate member "+app);}
            else check(all.add(entry),"no duplicate root app "+entry);
        }
        check(all.size()==AppCatalog.menuOrder(c).length,"all visible apps reachable exactly once");
        for(int app:AppCatalog.menuOrder(c))check(all.contains(app),"visible app retained "+app);
        for(int app:all)check(!AppCatalog.settingsOnly(app),"contextual app remains hidden "+app);
        check(folders<=5,"bounded folder count");
    }
    private void contracts()throws Exception{
        final Context base=getTargetContext();Context fixture=new ContextWrapper(base){@Override public SharedPreferences getSharedPreferences(String name,int mode){return base.getSharedPreferences(token+name,mode);}};
        check(AppCatalog.NAMES.length==50,"stable catalog unchanged");
        check(LauncherFolders.entries(fixture,-1).length==18,"13 direct apps plus five folders");coverage(fixture);
        int[] expected={7,5,4,4,3};for(int i=0;i<5;i++)check(LauncherFolders.entries(fixture,i).length==expected[i],"default folder size "+i);
        fixture.getSharedPreferences("appearance",0).edit().putInt("shortcut0",4).putInt(HomePersonalization.RIGHT_APP_KEY,10).commit();
        LauncherFolders.move(fixture,4,1);coverage(fixture);check(LauncherFolders.folder(fixture,4)==1,"move between folders");
        LauncherFolders.move(fixture,4,-1);check(contains(LauncherFolders.entries(fixture,-1),4),"move out to main menu");
        check(AppCatalog.shortcuts(fixture)[0]==4&&HomePersonalization.rightApp(fixture.getSharedPreferences("appearance",0))==10,"moves preserve actual shortcut IDs");
        for(int app:LauncherFolders.entries(fixture,4))LauncherFolders.move(fixture,app,-1);
        check(!contains(LauncherFolders.entries(fixture,-1),1004),"empty folder hidden");
        fixture.getSharedPreferences(LauncherFolders.PREFERENCES,0).edit().putInt("app4",999).putString("app10","bad").commit();
        check(LauncherFolders.folder(fixture,4)==0&&LauncherFolders.folder(fixture,10)==1,"invalid overrides fall back safely");
        int[] reverse=new int[50];for(int i=0;i<50;i++)reverse[i]=49-i;AppCatalog.save(fixture,reverse);
        check(Arrays.equals(reverse,AppCatalog.order(fixture)),"legacy global order preserved");
        int[] members=LauncherFolders.entries(fixture,0);check(members[0]==47,"folder follows existing saved app order");
        fixture.getSharedPreferences(LauncherFolders.PREFERENCES,0).edit().putBoolean("enabled",false).commit();
        check(Arrays.equals(LauncherFolders.entries(fixture,-1),AppCatalog.menuOrder(fixture)),"flat mode restores legacy order");
        fixture.getSharedPreferences("appearance",0).edit().putBoolean("simple-mode",true).commit();
        check(LauncherFolders.entries(fixture,-1).length==6&&LauncherFolders.entries(fixture,0).length==0,"simple mode remains six direct apps");
        check(BackupEngine.allowed(LauncherFolders.PREFERENCES,"app49","int")&&!BackupEngine.allowed(LauncherFolders.PREFERENCES,"app50","int"),"backup restricts real app IDs");
        check(!BackupEngine.allowed(LauncherFolders.PREFERENCES,"app1","string")&&BackupEngine.allowed(LauncherFolders.PREFERENCES,"enabled","boolean"),"backup restricts setting types");
        check(BackupEngine.category(LauncherFolders.PREFERENCES).equals("appearance"),"folder layout belongs to appearance backup");
        JSONObject group=new JSONObject().put("enabled",new JSONObject().put("type","boolean").put("value",true)).put("app4",new JSONObject().put("type","int").put("value",1));
        BackupEngine.applyPreferences(fixture,new JSONObject().put(LauncherFolders.PREFERENCES,group));
        check(LauncherFolders.folder(fixture,4)==1&&fixture.getSharedPreferences(LauncherFolders.PREFERENCES,0).getBoolean("enabled",false),"folder preferences restore together");
        JSONObject old=new JSONObject().put("preferences",new JSONObject().put("appearance",new JSONObject())).put("wallpaper",JSONObject.NULL);
        JSONObject current=new JSONObject().put("preferences",new JSONObject().put(LauncherFolders.PREFERENCES,group));
        JSONObject migrated=BackupEngine.selected(old,current,new HashSet<String>(Arrays.asList("appearance")));
        check(migrated.getJSONObject("preferences").getJSONObject(LauncherFolders.PREFERENCES).length()==0,"old appearance archives restore default folders instead of stale overrides");
    }
    private void navigation()throws Exception{
        openHome();select(1000);shot("menu-tools");press(KeyEvent.KEYCODE_DPAD_CENTER);
        check(screen().folder==0&&screen().focusedApp()==4,"OK enters tools");shot("tools");
        press(KeyEvent.KEYCODE_DPAD_RIGHT);check(screen().focusedApp()==13,"D-pad moves within folder");
        press(KeyEvent.KEYCODE_BACK);check(screen().folder==-1&&screen().focusedEntry()==1000&&screen().grid,"Back restores root folder focus");
        press(KeyEvent.KEYCODE_DPAD_CENTER);check(screen().focusedApp()==13,"folder remembers last member");
        select(4);press(KeyEvent.KEYCODE_DPAD_CENTER);
        for(int i=0;i<50&&!(resumed instanceof CalculatorActivity);i++)SystemClock.sleep(50);
        check(resumed instanceof CalculatorActivity,"OK launches actual Calculator destination");press(KeyEvent.KEYCODE_BACK);
        for(int i=0;i<50&&!(resumed instanceof MainActivity);i++)SystemClock.sleep(50);
        check(resumed instanceof MainActivity&&screen().folder==0&&screen().focusedApp()==4,"app Back returns to same folder and member");
        press(KeyEvent.KEYCODE_MENU);for(int i=0;i<4;i++)press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_DPAD_CENTER);
        check(KeypadDialog.current()!=null,"move action opens destination picker");press(KeyEvent.KEYCODE_DPAD_CENTER);
        check(LauncherFolders.folder(getTargetContext(),4)==-1,"picker moves Calculator to root");
        press(KeyEvent.KEYCODE_BACK);select(4);press(KeyEvent.KEYCODE_MENU);for(int i=0;i<4;i++)press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_DPAD_CENTER);
        press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_DPAD_CENTER);check(LauncherFolders.folder(getTargetContext(),4)==0,"picker returns Calculator to tools");
        SystemClock.sleep(3600); // Let the move confirmation disappear before documentation captures.
        select(1001);press(KeyEvent.KEYCODE_DPAD_CENTER);check(screen().folder==1,"open organizer");shot("organizer");press(KeyEvent.KEYCODE_SOFT_RIGHT);check(screen().folder==-1,"right softkey leaves folder");
        select(1002);shot("menu-media");press(KeyEvent.KEYCODE_DPAD_CENTER);shot("media");press(KeyEvent.KEYCODE_BACK);
        select(1003);press(KeyEvent.KEYCODE_DPAD_CENTER);shot("internet");press(KeyEvent.KEYCODE_BACK);
        select(1004);press(KeyEvent.KEYCODE_DPAD_CENTER);shot("advanced");press(KeyEvent.KEYCODE_BACK);
        ui(new Runnable(){public void run(){screen().enterFolder(0);screen().restoreEntryFocus(13);screen().saveMenuFocus();screen().folder=-1;screen().loadMenuFocus();}});
        check(screen().folder==-1&&screen().focusedEntry()==1000,"saved focus restores containing folder at root");
        press(KeyEvent.KEYCODE_DPAD_CENTER);check(screen().focusedApp()==13,"saved member focus survives reload");press(KeyEvent.KEYCODE_BACK);
        press(KeyEvent.KEYCODE_MENU);for(int i=0;i<3;i++)press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_DPAD_CENTER);
        check(!LauncherFolders.enabled(getTargetContext())&&screen().order.length==36,"Options restores full flat menu");
        press(KeyEvent.KEYCODE_MENU);for(int i=0;i<4;i++)press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_DPAD_CENTER);
        check(LauncherFolders.enabled(getTargetContext())&&screen().order.length==18,"Options restores five folders");
        ui(new Runnable(){public void run(){screen().enterFolder(4);LauncherFolders.move(getTargetContext(),23,-1);LauncherFolders.move(getTargetContext(),41,-1);screen().reloadAppearance();}});
        check(screen().folder==-1&&!contains(screen().order,1004),"moving final members exits empty folder safely");
        ui(new Runnable(){public void run(){LauncherFolders.move(getTargetContext(),23,4);LauncherFolders.move(getTargetContext(),41,4);screen().reloadAppearance();}});
        press(KeyEvent.KEYCODE_BACK);check(!screen().grid,"second Back returns dashboard");
    }
    private void presentation()throws Exception{
        ui(new Runnable(){public void run(){screen().grid=true;screen().leaveFolder();}});
        for(int theme=0;theme<Appearance.COLORS.length;theme++)for(int scale=0;scale<3;scale++){
            final int t=theme,s=scale;ui(new Runnable(){public void run(){getTargetContext().getSharedPreferences("appearance",0).edit().putInt("theme",t).putBoolean("list",s==1).commit();getTargetContext().getSharedPreferences("accessibility",0).edit().putInt("text",s).commit();screen().reloadAppearance();screen().restoreEntryFocus(1000);
                Bitmap image=Bitmap.createBitmap(240,302,Bitmap.Config.ARGB_8888);try{screen().draw(new Canvas(image));}finally{image.recycle();}
            }});check(screen().focusedEntry()==1000,"folder survives theme/text/list change "+t+"/"+s);
        }
        ui(new Runnable(){public void run(){getTargetContext().getSharedPreferences("appearance",0).edit().putInt("theme",17).putBoolean("list",true).commit();screen().reloadAppearance();}});shot("folders-list-large");
        ui(new Runnable(){public void run(){getTargetContext().getSharedPreferences("appearance",0).edit().putBoolean("list",false).commit();getTargetContext().getSharedPreferences("accessibility",0).edit().putInt("text",0).commit();screen().reloadAppearance();}});
    }
    public void onCreate(Bundle args){start();}
    public void onStart(){Bundle result=new Bundle();try{
        getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        for(String name:new String[]{"appearance","accessibility",LauncherFolders.PREFERENCES,"launcher-focus","home-menu","setup","keyguard"})snapshot(name);
        awake=((PowerManager)getTargetContext().getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"keyshell:folder-checks");awake.acquire(240000);
        contracts();
        ui(new Runnable(){public void run(){getTargetContext().getSharedPreferences("appearance",0).edit().remove("app-order").putInt(HomeStyle.KEY,0).putBoolean("simple-mode",false).putBoolean("list",false).putInt("theme",17).commit();getTargetContext().getSharedPreferences(LauncherFolders.PREFERENCES,0).edit().clear().commit();getTargetContext().getSharedPreferences("accessibility",0).edit().putInt("text",0).putBoolean("contrast",false).commit();getTargetContext().getSharedPreferences("setup",0).edit().putBoolean("complete",true).commit();getTargetContext().getSharedPreferences("keyguard",0).edit().putBoolean("locked",false).commit();}});
        shots=new File(getTargetContext().getFilesDir(),"launcher-folder-shots");shots.mkdirs();navigation();presentation();result.putString("result","PASS "+checks+" launcher folder checks");
    }catch(Throwable error){result.putString("error","after "+checks+" checks: "+android.util.Log.getStackTraceString(error));}finally{
        try{ui(new Runnable(){public void run(){KeypadDialog dialog=KeypadDialog.current();if(dialog!=null&&dialog.isShowing())dialog.dismiss();}});restore();if(home!=null)ui(new Runnable(){public void run(){screen().folder=-1;screen().reloadAppearance();screen().loadMenuFocus();screen().grid=false;}});if(awake!=null&&awake.isHeld())awake.release();
            for(String name:new String[]{"appearance",LauncherFolders.PREFERENCES})getTargetContext().deleteSharedPreferences(token+name);
        }catch(Throwable error){result.putString("restore_error",android.util.Log.getStackTraceString(error));}
    }
    try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"launcher-folders-result.txt"))){out.write(result.toString().getBytes("UTF-8"));}catch(IOException ignored){}
    finish(result.containsKey("error")||result.containsKey("restore_error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);}
}
