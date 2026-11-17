package ro.leo.visual;

import android.app.Instrumentation;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Isolated daily-use checks. Files and screenshots belong only to this run. */
public final class KeyShellDailyUiTests extends Instrumentation {
    private LeoActivity page;
    private int checks;
    private File fixtures,shots;
    private PowerManager.WakeLock awake;
    private final Map<String,Map<String,?>> saved=new HashMap<>();
    interface Condition { boolean ready() throws Exception; }
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private void ui(final Runnable task){final Throwable[] error={null};runOnMainSync(new Runnable(){public void run(){try{task.run();}catch(Throwable failure){error[0]=failure;}}});if(error[0]!=null)throw new RuntimeException(error[0]);}
    private Object field(Object owner,String name){try{for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(owner);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}catch(Exception error){throw new RuntimeException(error);}}
    private void call(Object owner,String name,Class<?>[] types,Object... args){try{Method method=owner.getClass().getDeclaredMethod(name,types);method.setAccessible(true);method.invoke(owner,args);}catch(Exception error){throw new RuntimeException(error);}}
    private void await(String message,final Condition condition){long end=SystemClock.elapsedRealtime()+8000;final boolean[] ready={false};do{ui(new Runnable(){public void run(){try{ready[0]=condition.ready();}catch(Exception error){throw new RuntimeException(error);}}});if(ready[0])break;SystemClock.sleep(30);}while(SystemClock.elapsedRealtime()<end);check(ready[0],message);waitForIdleSync();}
    private void launch(Class<?> target){launch(new Intent(getTargetContext(),target));}
    private void launch(Intent intent){page=(LeoActivity)startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("page laid out",new Condition(){public boolean ready(){View view=page.getWindow().getDecorView();return view.hasWindowFocus()&&view.isLaidOut()&&view.getWidth()>0&&view.getHeight()>0;}});}
    private void close(){if(page!=null){ui(new Runnable(){public void run(){page.finish();}});waitForIdleSync();page=null;}}
    private KeyEvent event(long down,int action,int key,int repeats,int flags){return new KeyEvent(down,SystemClock.uptimeMillis(),action,key,repeats,0,0,0,flags);}
    private void dispatch(final long down,final int action,final int key,final int repeats,final int flags){ui(new Runnable(){public void run(){KeypadDialog dialog=KeypadDialog.current();if(dialog!=null)dialog.dispatchKeyEvent(event(down,action,key,repeats,flags));else page.dispatchKeyEvent(event(down,action,key,repeats,flags));}});waitForIdleSync();}
    private void press(int key){long down=SystemClock.uptimeMillis();dispatch(down,0,key,0,0);dispatch(down,1,key,0,0);}
    private void back(){press(KeyEvent.KEYCODE_BACK);}
    private ListView list(){return (ListView)field(page,"activeList");}
    private void shot(String name){
        final CountDownLatch drawn=new CountDownLatch(1);
        ui(new Runnable(){public void run(){final View decor=page.getWindow().getDecorView();final ViewTreeObserver.OnDrawListener[] listener={null};listener[0]=new ViewTreeObserver.OnDrawListener(){public void onDraw(){decor.post(new Runnable(){public void run(){if(decor.getViewTreeObserver().isAlive())decor.getViewTreeObserver().removeOnDrawListener(listener[0]);drawn.countDown();}});}};decor.getViewTreeObserver().addOnDrawListener(listener[0]);decor.invalidate();}});
        try{check(drawn.await(4,TimeUnit.SECONDS),"screenshot waits for drawn content");}catch(InterruptedException error){throw new RuntimeException(error);}SystemClock.sleep(300);
        Bitmap image=getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();check(image!=null,"screenshot available");try(FileOutputStream out=new FileOutputStream(new File(shots,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}catch(IOException error){throw new RuntimeException(error);}finally{image.recycle();}
    }
    private void snapshot(String name){saved.put(name,new HashMap<String,Object>(getTargetContext().getSharedPreferences(name,0).getAll()));}
    @SuppressWarnings("unchecked") private void restore(){for(Map.Entry<String,Map<String,?>> entry:saved.entrySet()){SharedPreferences.Editor editor=getTargetContext().getSharedPreferences(entry.getKey(),0).edit().clear();for(Map.Entry<String,?> value:entry.getValue().entrySet()){Object item=value.getValue();String key=value.getKey();if(item instanceof String)editor.putString(key,(String)item);else if(item instanceof Boolean)editor.putBoolean(key,(Boolean)item);else if(item instanceof Integer)editor.putInt(key,(Integer)item);else if(item instanceof Long)editor.putLong(key,(Long)item);else if(item instanceof Float)editor.putFloat(key,(Float)item);else if(item instanceof Set)editor.putStringSet(key,new HashSet<String>((Set<String>)item));}editor.commit();}}

    private void catalog() throws Exception {
        check(AppCatalog.NAMES.length==48,"stable catalog size");
        for(int id=0;id<48;id++){Intent intent=AppCatalog.intent(getTargetContext(),id);check(intent!=null&&intent.getComponent()!=null,"native app route "+id);check(getTargetContext().getPackageManager().getActivityInfo(intent.getComponent(),0)!=null,"declared app destination "+id);}
        check("video".equals(AppCatalog.intent(getTargetContext(),12).getStringExtra("kind")),"video extra preserved");
        check(AppCatalog.intent(getTargetContext(),17).getBooleanExtra("favorites",false),"favorites extra preserved");
        check(AppCatalog.intent(getTargetContext(),39).getBooleanExtra("podcast",false),"podcast extra preserved");
        check(AppCatalog.intent(getTargetContext(),-1)==null&&AppCatalog.intent(getTargetContext(),48)==null,"invalid IDs rejected");
        Intent publicEntry=new Intent(getTargetContext(),MainActivity.class).putExtra("open_app",18).putExtra("open_menu",true).putExtra("edit_home",true);
        Intent clean=HomeAccess.sanitize(publicEntry);
        check(!clean.hasExtra("open_app")&&!clean.hasExtra("open_menu")&&!clean.hasExtra("edit_home"),"public Home ignores internal commands");
        check(publicEntry.hasExtra("open_app"),"caller intent not mutated");
        check(HomeAccess.sanitize(HomeAccess.intent(getTargetContext()).putExtra("open_app",7)).getIntExtra("open_app",-1)==7,"internal route retains stable extra");
        check(!getTargetContext().getPackageManager().getActivityInfo(HomeAccess.intent(getTargetContext()).getComponent(),0).exported,"internal alias is private");
        ActivityMonitor monitor=addMonitor(FilesActivity.class.getName(),null,false);
        getTargetContext().startActivity(HomeAccess.intent(getTargetContext()).putExtra("open_app",7).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        page=(LeoActivity)waitForMonitorWithTimeout(monitor,5000);removeMonitor(monitor);
        check(page instanceof FilesActivity,"private Home entry actually opens stable app ID 7");close();
    }

    private void weatherAndKeys(){
        long now=100000000L;
        check(!WeatherFreshness.stale(now,now-3599999),"fresh saved weather");
        check(WeatherFreshness.stale(now,now-21600000),"six-hour weather marked old");
        check(WeatherFreshness.stale(now,0),"missing time is not fresh");
        check(WeatherFreshness.stale(now,now+300001),"future observation detected");
        check(!WeatherFreshness.label(now,now-7200000).equals(WeatherFreshness.label(now,now-21600000)),"fresh and old labels differ");
        KeyRelease release=new KeyRelease();long down=SystemClock.uptimeMillis();
        check(!release.accept(event(down,0,23,0,0)),"press waits for release");
        check(!release.accept(event(down,0,23,1,0)),"held repeat does not accept");
        check(!release.accept(event(down,1,23,0,KeyEvent.FLAG_CANCELED)),"canceled press ignored");
        check(!release.accept(event(down,1,23,0,0)),"orphan release ignored");
        release.accept(event(down,0,23,0,0));check(release.accept(event(down,1,23,0,0)),"complete press accepts once");
        check(!release.accept(event(down,1,23,0,0)),"duplicate release ignored");
    }

    private void awaitDirectory(final File directory){await("directory loaded",new Condition(){public boolean ready(){return directory.equals(field(page,"current"))&&field(page,"listing")==page.root&&list()!=null;}});}
    @SuppressWarnings("unchecked") private int fileIndex(String name){List<File> files=(List<File>)field(page,"entries");for(int i=0;i<files.size();i++)if(name.equals(files.get(i).getName()))return i;throw new AssertionError("Missing fixture "+name);}
    private void selectFile(final String name){ui(new Runnable(){public void run(){ListView view=list();view.requestFocus();view.setSelection(fileIndex(name));}});await("file selection ready",new Condition(){public boolean ready(){File selected=(File)field(page,"selected");return selected!=null&&selected.getName().equals(name);}});}
    private void files() throws Exception {
        File first=new File(fixtures,"A"),second=new File(fixtures,"B");check(first.mkdir()&&second.mkdir(),"isolated folders created");
        try(FileOutputStream output=new FileOutputStream(new File(fixtures,"Text demonstrativ cu un nume lung.txt"))){output.write("KeyShell fixture".getBytes("UTF-8"));}
        launch(new Intent(getTargetContext(),FilesActivity.class).putExtra("folder",fixtures.getPath()));awaitDirectory(fixtures);
        selectFile("B");press(KeyEvent.KEYCODE_DPAD_CENTER);awaitDirectory(second);
        check(list().getCount()==0,"empty folder has no entries");back();awaitDirectory(fixtures);
        check(((File)field(page,"selected")).getName().equals("B"),"Back restores the parent folder selection");
        selectFile("Text demonstrativ cu un nume lung.txt");long down=SystemClock.uptimeMillis();
        dispatch(down,0,KeyEvent.KEYCODE_5,0,0);check(((Set<?>)field(page,"marked")).isEmpty(),"file not marked on key down");
        dispatch(down,1,KeyEvent.KEYCODE_5,0,KeyEvent.FLAG_CANCELED);check(((Set<?>)field(page,"marked")).isEmpty(),"canceled marking ignored");
        press(KeyEvent.KEYCODE_5);awaitDirectory(fixtures);check(((Set<?>)field(page,"marked")).size()==1,"one released key marks one file");
        shot("files");
        ui(new Runnable(){public void run(){call(page,"textFile",new Class<?>[]{File.class},new Object[]{null});}});waitForIdleSync();
        final ArrayList<EditText> editors=new ArrayList<>();collectEditors(page.root,editors);check(editors.size()==2,"new text form has name and body");
        ui(new Runnable(){public void run(){editors.get(1).setText("Unsaved daily draft");page.onBackPressed();}});waitForIdleSync();
        check(KeypadDialog.current()!=null,"dirty draft asks before leaving");press(KeyEvent.KEYCODE_DPAD_CENTER);
        check("Unsaved daily draft".equals(editors.get(1).getText().toString()),"continue retains draft");
        ui(new Runnable(){public void run(){page.onBackPressed();}});waitForIdleSync();press(KeyEvent.KEYCODE_DPAD_DOWN);press(KeyEvent.KEYCODE_DPAD_CENTER);awaitDirectory(fixtures);
        check(new File(fixtures,"Text demonstrativ cu un nume lung.txt").length()==16,"original fixture untouched");close();
    }
    private void collectEditors(View view,List<EditText> result){if(view instanceof EditText)result.add((EditText)view);if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)collectEditors(((ViewGroup)view).getChildAt(i),result);}
    private void shell(String command){try(InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).executeShellCommand(command))){byte[] bytes=new byte[1024];while(input.read(bytes)>=0){}}catch(IOException error){throw new RuntimeException(error);}}

    @SuppressWarnings("unchecked") private void gallery() throws Exception {
        final ArrayList<Uri> samples=new ArrayList<>();
        String[] assets={"Illus_Peony_Aqua.jpg","Illus_Flow2_Orange.jpg","Illus_Flow2_Sapphire.jpg"};
        for(int index=0;index<4;index++){File image=new File(fixtures,"sample-"+index+".jpg");try(InputStream in=getTargetContext().getAssets().open("wallpapers/gaia/"+assets[index%3]);FileOutputStream out=new FileOutputStream(image)){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))>0)out.write(bytes,0,n);}samples.add(LeoFileProvider.uri(getTargetContext(),image));}
        final Uri uri=samples.get(0);
        launch(new Intent(getTargetContext(),GalleryActivity.class).setData(uri).putExtra("title","Imagine demonstrativă"));
        ui(new Runnable(){public void run(){List<Uri> images=(List<Uri>)field(page,"images");List<String> names=(List<String>)field(page,"names");images.clear();names.clear();for(int i=0;i<4;i++){images.add(samples.get(i));names.add("Gaia · imagine demonstrativă "+(i+1));}call(page,"grid",new Class<?>[]{});}});
        await("four thumbnails decoded",new Condition(){public boolean ready(){return ((Map<?,?>)field(page,"thumbs")).size()==4;}});
        press(KeyEvent.KEYCODE_DPAD_RIGHT);check((Integer)field(page,"selected")==1,"gallery grid moves by one");
        long down=SystemClock.uptimeMillis();dispatch(down,0,23,0,0);check(!(Boolean)field(page,"viewer"),"gallery opens on release");dispatch(down,1,23,0,KeyEvent.FLAG_CANCELED);check(!(Boolean)field(page,"viewer"),"canceled gallery OK ignored");
        press(23);await("full image decoded",new Condition(){public boolean ready(){return field(page,"photo")!=null;}});check((Boolean)field(page,"viewer"),"valid gallery OK opens photo");back();
        check(!(Boolean)field(page,"viewer")&&(Integer)field(page,"selected")==1,"viewer Back retains grid selection");shot("gallery");close();
    }

    private void media(){launch(new Intent(getTargetContext(),MediaActivity.class).putExtra("now",true).putExtra("preview",true));check(page.root!=null&&page.root.isShown(),"player uses shared native page");check(page.helpControls().contains("Demo"),"player footer exposes demo state");shot("player");long down=SystemClock.uptimeMillis();dispatch(down,0,KeyEvent.KEYCODE_MENU,0,0);check(KeypadDialog.current()==null,"softkey waits for release");dispatch(down,1,KeyEvent.KEYCODE_MENU,0,KeyEvent.FLAG_CANCELED);check(KeypadDialog.current()==null,"canceled softkey does not open options");press(KeyEvent.KEYCODE_MENU);check(KeypadDialog.current()!=null,"released softkey opens player options");back();close();}

    private void lock(){
        launch(KeypadLockActivity.class);check(KeypadLockActivity.locked(getTargetContext()),"lock active");shot("lock");
        press(KeyEvent.KEYCODE_STAR);check(KeypadLockActivity.locked(getTargetContext()),"star alone cannot unlock");
        long down=SystemClock.uptimeMillis();dispatch(down,0,KeyEvent.KEYCODE_MENU,0,0);check((Long)field(page,"armed")==0,"unlock waits for softkey release");dispatch(down,1,KeyEvent.KEYCODE_MENU,0,KeyEvent.FLAG_CANCELED);check((Long)field(page,"armed")==0,"canceled unlock softkey ignored");
        press(KeyEvent.KEYCODE_MENU);check((Long)field(page,"armed")>0,"released softkey arms unlock");press(KeyEvent.KEYCODE_2);check((Long)field(page,"armed")==0,"wrong second key disarms unlock");
        press(KeyEvent.KEYCODE_MENU);down=SystemClock.uptimeMillis();dispatch(down,0,KeyEvent.KEYCODE_STAR,0,0);check(KeypadLockActivity.locked(getTargetContext()),"star down does not unlock");dispatch(down,1,KeyEvent.KEYCODE_STAR,0,KeyEvent.FLAG_CANCELED);check(KeypadLockActivity.locked(getTargetContext()),"canceled star does not unlock");
        press(KeyEvent.KEYCODE_MENU);press(KeyEvent.KEYCODE_STAR);check(!KeypadLockActivity.locked(getTargetContext()),"complete softkey-star sequence unlocks");page=null;
    }

    private void screenTicker(){
        launch(HelpActivity.class);final int[] count={0};final ScreenTicker[] ticker={null};
        ui(new Runnable(){public void run(){ticker[0]=new ScreenTicker(page,new ScreenTicker.Work(){public long refresh(){count[0]++;return 40;}});ticker[0].start();}});
        SystemClock.sleep(120);check(count[0]>1,"visible ticker refreshes");
        awake.release();shell("input keyevent 223");
        await("display sleeps",new Condition(){public boolean ready(){return !((PowerManager)getTargetContext().getSystemService(Context.POWER_SERVICE)).isInteractive();}});
        SystemClock.sleep(100);final int asleep=count[0];SystemClock.sleep(180);check(count[0]==asleep,"no ticker refresh while display is off");
        shell("input keyevent 224");awake.acquire(180000);
        await("display wake restarts ticker",new Condition(){public boolean ready(){return count[0]>asleep;}});
        ui(new Runnable(){public void run(){ticker[0].stop();}});int stopped=count[0];SystemClock.sleep(120);check(count[0]==stopped,"paused ticker stays stopped");close();
    }

    public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    public void onStart(){Bundle result=new Bundle();int code=0;try{
        for(String name:new String[]{"appearance","keyguard","home-menu"})snapshot(name);
        awake=((PowerManager)getTargetContext().getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"KeyShell:daily-tests");awake.acquire(180000);
        fixtures=new File(getTargetContext().getFilesDir(),"documents/Exemple");if(fixtures.exists())fixtures=new File(getTargetContext().getFilesDir(),"documents/Exemple-"+SystemClock.elapsedRealtime());check(fixtures.mkdirs(),"isolated fixture root");shots=new File(getTargetContext().getFilesDir(),"daily-polish-shots");shots.mkdirs();
        catalog();weatherAndKeys();files();gallery();media();lock();screenTicker();
        result.putString("result","PASS "+checks+" daily polish checks");code=-1;
    }catch(Throwable failure){result.putString("result","FAIL after "+checks+" checks: "+failure);StringWriter text=new StringWriter();failure.printStackTrace(new PrintWriter(text));result.putString("trace",text.toString());try{shot("failure");}catch(Throwable ignored){}
    }finally{close();restore();if(awake!=null&&awake.isHeld())awake.release();if(fixtures!=null)remove(fixtures);}
        finish(code,result);
    }
    private void remove(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)remove(child);file.delete();}
}
