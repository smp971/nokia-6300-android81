package ro.leo.visual;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Owned fixtures only; no microphone, radio toggle, communications or installation is started here. */
public final class ShellOptimizationTests extends Instrumentation {
    private LeoActivity page,cover;
    private int checks;
    private File fixtures,shots;
    private PowerManager.WakeLock awake;
    private final Map<String,Map<String,?>> saved=new HashMap<String,Map<String,?>>();
    private interface Ready {boolean ready();}
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
    private void ui(final Runnable task){final Throwable[] failure={null};runOnMainSync(new Runnable(){public void run(){try{task.run();}catch(Throwable error){failure[0]=error;}}});if(failure[0]!=null)throw new RuntimeException(failure[0]);}
    private Object field(Object object,String name){try{for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}catch(Exception error){throw new RuntimeException(error);}}
    private void await(String message,final Ready ready){final boolean[] done={false};long until=SystemClock.uptimeMillis()+8000;do{ui(new Runnable(){public void run(){done[0]=ready.ready();}});if(done[0])break;SystemClock.sleep(25);}while(SystemClock.uptimeMillis()<until);check(done[0],message);waitForIdleSync();}
    private void window(final Activity activity){await("window focused: "+activity.getClass().getSimpleName(),new Ready(){public boolean ready(){return activity.hasWindowFocus();}});}
    private void open(Intent intent){page=(LeoActivity)startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));window(page);SystemClock.sleep(2600);}
    private void open(Class<?> type){open(new Intent(getTargetContext(),type));}
    private void close(){if(page!=null){final Activity current=page;ui(new Runnable(){public void run(){current.finish();}});page=null;waitForIdleSync();}}
    private void cover(){cover=(LeoActivity)startActivitySync(new Intent(getTargetContext(),ThemePacksActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));window(cover);}
    private void uncover(){final Activity current=cover;ui(new Runnable(){public void run(){current.finish();}});cover=null;window(page);}
    private void press(final int key){ui(new Runnable(){public void run(){long now=SystemClock.uptimeMillis();page.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,key,0));page.dispatchKeyEvent(new KeyEvent(now,now+30,KeyEvent.ACTION_UP,key,0));}});waitForIdleSync();}
    private void cancel(final int key){ui(new Runnable(){public void run(){long now=SystemClock.uptimeMillis();page.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,key,0));page.dispatchKeyEvent(new KeyEvent(now,now+30,KeyEvent.ACTION_UP,key,0,0,-1,0,KeyEvent.FLAG_CANCELED));}});waitForIdleSync();}
    private ListView list(){return (ListView)field(page,"activeList");}
    private void snapshot(String name){saved.put(name,new HashMap<String,Object>(getTargetContext().getSharedPreferences(name,0).getAll()));}
    @SuppressWarnings("unchecked") private void restore(){for(Map.Entry<String,Map<String,?>> group:saved.entrySet()){SharedPreferences.Editor editor=getTargetContext().getSharedPreferences(group.getKey(),0).edit().clear();for(Map.Entry<String,?> item:group.getValue().entrySet()){Object value=item.getValue();String key=item.getKey();if(value instanceof String)editor.putString(key,(String)value);else if(value instanceof Integer)editor.putInt(key,(Integer)value);else if(value instanceof Long)editor.putLong(key,(Long)value);else if(value instanceof Float)editor.putFloat(key,(Float)value);else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);else if(value instanceof Set)editor.putStringSet(key,new HashSet<String>((Set<String>)value));}check(editor.commit(),"preferences restored: "+group.getKey());}}
    private void shot(String name)throws Exception{SystemClock.sleep(350);Bitmap image=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();check(image!=null,"screenshot "+name);try(FileOutputStream out=new FileOutputStream(new File(shots,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
    private void forecast()throws Exception{
        JSONObject data=new JSONObject(),current=new JSONObject(),daily=new JSONObject();
        current.put("temperature_2m",18).put("apparent_temperature",16).put("relative_humidity_2m",61).put("wind_speed_10m",12).put("weather_code",2);data.put("current",current).put("daily",daily).put("timezone","Europe/Bucharest");
        for(String key:new String[]{"time","temperature_2m_min","temperature_2m_max","precipitation_probability_max","weather_code","sunrise","sunset"}){
            JSONArray values=new JSONArray();for(int i=0;i<7;i++)values.put(key.equals("time")?"2026-11-"+(21+i):key.equals("sunrise")?"2026-11-21T07:20":key.equals("sunset")?"2026-11-21T16:45":key.equals("weather_code")?2:key.equals("temperature_2m_min")?8:key.equals("temperature_2m_max")?18:20);daily.put(key,values);
        }
        getTargetContext().getSharedPreferences("weather",0).edit().putString("city","București").putString("data",data.toString()).putLong("updated",System.currentTimeMillis()).commit();
    }
    private int pixel(Drawable drawable,int... states){Bitmap bitmap=Bitmap.createBitmap(100,48,Bitmap.Config.ARGB_8888);try{drawable.setBounds(0,0,100,48);drawable.setState(states);drawable.draw(new Canvas(bitmap));return bitmap.getPixel(50,24);}finally{bitmap.recycle();}}
    private void presentation()throws Exception{
        final Context context=getTargetContext();final SharedPreferences access=context.getSharedPreferences("accessibility",0);
        for(int scale=0;scale<3;scale++){
            access.edit().putInt("text",scale).putBoolean("contrast",false).commit();
            final float factor=Appearance.textScale(context);
            ui(new Runnable(){public void run(){
                Paint paint=new Paint();Bitmap bitmap=Bitmap.createBitmap(240,36,Bitmap.Config.ARGB_8888);
                try{UiChrome.header(new Canvas(bitmap),"Titlu foarte lung care depășește ecranul",Appearance.palette(context),paint,factor);check(Math.abs(paint.getTextSize()-22*factor)<.01,"Canvas heading uses scale");Paint.FontMetrics metrics=paint.getFontMetrics();check(metrics.descent-metrics.ascent<=36,"heading fits its vertical bounds");}finally{bitmap.recycle();}
                for(int index=0;index<Appearance.COLORS.length;index++){
                    Appearance.Palette palette=new Appearance.Palette(Appearance.COLORS[index]);Drawable row=UiChrome.row(palette,0,1);
                    check(pixel(row,android.R.attr.state_enabled,android.R.attr.state_selected)==palette.selection,"selected row "+index);
                    check(pixel(row)==palette.background,"disabled row "+index);
                    check(UiChrome.textColors(palette).getColorForState(new int[]{android.R.attr.state_enabled,android.R.attr.state_selected},0)==palette.ink,"selected ink "+index);
                }
            }});
            open(WeatherActivity.class);for(int tab=0;tab<3;tab++){shot("weather-"+scale+"-"+tab);press(KeyEvent.KEYCODE_DPAD_RIGHT);}cancel(KeyEvent.KEYCODE_DPAD_RIGHT);check((Integer)field(page,"page")==0,"canceled weather navigation keeps tab");close();
            open(CalculatorActivity.class);press(KeyEvent.KEYCODE_1);press(KeyEvent.KEYCODE_DPAD_UP);press(KeyEvent.KEYCODE_2);press(KeyEvent.KEYCODE_DPAD_CENTER);
            check("3".equals(field(page,"expression")),"calculator result preserved with scaled frame");cancel(KeyEvent.KEYCODE_7);check("3".equals(field(page,"expression")),"canceled calculator digit ignored");shot("calculator-"+scale);close();
        }
        access.edit().putInt("text",0).commit();open(TerminalActivity.class);
        final EditText command=(EditText)field(page,"command");final TextView output=(TextView)field(page,"output");final Object root=page.root;
        final float before=command.getTextSize();ui(new Runnable(){public void run(){command.setText("draft-keypad");command.setSelection(5);}});
        cover();access.edit().putInt("text",2).commit();uncover();
        check(page.root==root&&command==field(page,"command"),"font change preserves existing terminal views");
        check("draft-keypad".contentEquals(command.getText())&&command.getSelectionStart()==5,"font change preserves draft and caret");
        check(command.getTextSize()>before*1.25f,"manual command field grows on return");
        check(output.getTextSize()>18*context.getResources().getDisplayMetrics().scaledDensity,"terminal output grows on return");shot("terminal-large");close();
        access.edit().putInt("text",0).commit();
        open(PhoneActivity.class);final EditText number=(EditText)field(page,"number");
        ui(new Runnable(){public void run(){number.setText("123");number.setSelection(2);}});
        cover();access.edit().putInt("text",2).commit();uncover();
        check("123".contentEquals(number.getText())&&number.getSelectionStart()==2,"dialer retains number and caret after scale change; no call made");
        check(Math.abs(number.getTextSize()-23*1.3f*context.getResources().getDisplayMetrics().scaledDensity)<1,"dialer retains its own logical font size");close();
        access.edit().putInt("text",0).commit();
    }
    @SuppressWarnings("unchecked") private void rows()throws Exception{
        open(QuickSettingsActivity.class);final ListView view=list();final Object root=page.root,adapter=view.getAdapter();
        ui(new Runnable(){public void run(){view.setSelectionFromTop(5,28);}});waitForIdleSync();
        final int selected=view.getSelectedItemPosition(),first=view.getFirstVisiblePosition(),top=view.getChildAt(0).getTop();
        ui(new Runnable(){public void run(){ArrayList<String> values=new ArrayList<String>((List<String>)field(page,"listRows"));values.set(0,"Wi-Fi · stare de probă");check(page.replaceRows("Configurări rapide",values),"incremental rows accepted");}});
        waitForIdleSync();check(root==page.root&&adapter==view.getAdapter(),"status update retains root/list/adapter identity");
        check(view.hasFocus()&&view.getSelectedItemPosition()==selected,"status update retains focus and selection");
        check(view.getFirstVisiblePosition()==first&&view.getChildAt(0).getTop()==top,"offscreen status update retains exact scroll offset");
        ui(new Runnable(){public void run(){((BroadcastReceiver)field(page,"updates")).onReceive(page,new Intent(android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION));}});waitForIdleSync();
        check(root==page.root&&view==list()&&view.getSelectedItemPosition()==selected,"real receiver refresh retains page and selection");
        check(QuickSettingsActivity.wifiState(android.net.wifi.WifiManager.WIFI_STATE_ENABLING).contains("pornește"),"Wi-Fi enabling state");
        check(QuickSettingsActivity.bluetoothState(android.bluetooth.BluetoothAdapter.STATE_TURNING_OFF).contains("oprește"),"Bluetooth disabling state");
        shot("quick-settings");close();
    }
    private boolean scheduled(ScreenTicker ticker){
        try{Handler handler=(Handler)field(ticker,"handler");Object queue=field(handler,"mQueue");synchronized(queue){Object message=field(queue,"mMessages");while(message!=null){Message current=(Message)message;if(current.getTarget()==handler&&current.getCallback()==field(ticker,"tick"))return true;message=field(message,"next");}}return false;}catch(RuntimeException error){throw error;}
    }
    private void recorder()throws Exception{
        check(RecordingService.instance==null,"no real recording running before lifecycle fixture");open(RecorderActivity.class);
        final ScreenTicker ticker=(ScreenTicker)field(page,"ticker");ui(new Runnable(){public void run(){check(!scheduled(ticker),"idle recorder has no refresh message");}});
        final String oldState=RecordingService.state;final boolean oldPaused=RecordingService.paused;
        final long oldStarted=RecordingService.started,oldPausedAt=RecordingService.pausedAt,oldPausedTotal=RecordingService.pausedTotal;
        try{
            ui(new Runnable(){public void run(){RecordingService.instance=new RecordingService();RecordingService.started=SystemClock.elapsedRealtime();RecordingService.pausedTotal=0;RecordingService.paused=false;ticker.refresh();check(scheduled(ticker),"active recording meters refresh");RecordingService.paused=true;RecordingService.pausedAt=SystemClock.elapsedRealtime();ticker.refresh();check(!scheduled(ticker),"paused recording has no refresh message");RecordingService.paused=false;ticker.refresh();}});
            cover();ui(new Runnable(){public void run(){check(!scheduled(ticker),"covered recorder stops metering");}});uncover();
            ui(new Runnable(){public void run(){check(scheduled(ticker),"returning to active recording restarts metering");}});
        }finally{ui(new Runnable(){public void run(){RecordingService.instance=null;RecordingService.state=oldState;RecordingService.paused=oldPaused;RecordingService.started=oldStarted;RecordingService.pausedAt=oldPausedAt;RecordingService.pausedTotal=oldPausedTotal;ticker.refresh();}});}
        ui(new Runnable(){public void run(){check(!scheduled(ticker),"stopped recording cancels metering");}});shot("recorder-idle");close();
    }
    private void memory()throws Exception{
        open(QuickSettingsActivity.class);
        ui(new Runnable(){public void run(){
            UiRowCache rows=(UiRowCache)field(page,"rowCache");rows.get("fixture\nsecondary");check(rows.size()>0,"styled row cache populated");
            RowArtwork artwork=new RowArtwork(page);Bitmap image=Bitmap.createBitmap(40,40,Bitmap.Config.RGB_565);artwork.cache.put("fixture",image);Drawable bound=artwork.get("fixture",null,"Fixture");
            ((LeoApplication)page.getApplication()).onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW);
            check(rows.size()==0&&artwork.cache.size()==0,"application pressure frees row and artwork caches");check(!image.isRecycled()&&((BitmapDrawable)bound).getBitmap()==image,"bound bitmap remains drawable after eviction");artwork.close();
        }});close();
        File sample=new File(fixtures,"memory-image.jpg");Bitmap pattern=Bitmap.createBitmap(320,240,Bitmap.Config.RGB_565);
        try(FileOutputStream output=new FileOutputStream(sample)){Canvas canvas=new Canvas(pattern);canvas.drawColor(0xff1d6575);Paint paint=new Paint();paint.setColor(0xffffbc58);canvas.drawCircle(160,120,70,paint);pattern.compress(Bitmap.CompressFormat.JPEG,90,output);}finally{pattern.recycle();}
        final Uri uri=LeoFileProvider.uri(getTargetContext(),sample);open(new Intent(getTargetContext(),GalleryActivity.class).setData(uri).putExtra("title","Imagine de probă"));
        await("gallery fixture decoded",new Ready(){public boolean ready(){return field(page,"photo")!=null;}});
        ui(new Runnable(){public void run(){((GalleryActivity)page).trimUiMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE);check(field(page,"photo")!=null,"foreground viewer survives memory pressure");}});
        press(KeyEvent.KEYCODE_DPAD_CENTER);final int zoom=(Integer)field(page,"zoom");cover();
        ui(new Runnable(){public void run(){((LeoApplication)page.getApplication()).onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);check(field(page,"photo")==null,"hidden gallery releases decoded photo");}});
        uncover();await("gallery photo reloads on return",new Ready(){public boolean ready(){return field(page,"photo")!=null;}});
        check((Integer)field(page,"zoom")==zoom&&(Boolean)field(page,"viewer"),"memory reload preserves viewer and zoom");shot("gallery-restored");close();
    }
    private void remove(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)remove(child);file.delete();}
    public void onCreate(Bundle args){start();}
    public void onStart(){Bundle result=new Bundle();try{
        getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        awake=((PowerManager)getTargetContext().getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"keyshell:shell-checks");awake.acquire(10*60*1000L);
        for(String name:new String[]{"appearance","accessibility","weather"})snapshot(name);
        fixtures=new File(getTargetContext().getFilesDir(),"documents/shell-six-"+UUID.randomUUID());check(fixtures.mkdirs(),"owned fixture folder created");shots=new File(getTargetContext().getFilesDir(),"shell-six-shots");shots.mkdirs();
        forecast();presentation();rows();recorder();memory();result.putString("result","PASS "+checks+" shell optimization checks");
    }catch(Throwable failure){result.putString("error","after "+checks+" checks: "+LogTrace(failure));}finally{
        try{if(cover!=null){final Activity current=cover;ui(new Runnable(){public void run(){current.finish();}});}close();restore();if(fixtures!=null)remove(fixtures);if(awake!=null&&awake.isHeld())awake.release();}catch(Throwable failure){result.putString("restore_error",LogTrace(failure));}
    }finish(result.containsKey("error")||result.containsKey("restore_error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);}
    private String LogTrace(Throwable error){return android.util.Log.getStackTraceString(error);}
}
