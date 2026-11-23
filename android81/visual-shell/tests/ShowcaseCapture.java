package ro.leo.visual;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import org.json.*;

/** Reproducible documentation scenes. No carrier traffic, audio playback, recording or external web requests. */
public final class ShowcaseCapture extends Instrumentation {
    private Activity page;
    private int captures;
    private File fixtures,shots;
    private PowerManager.WakeLock awake;
    private ServerSocket server;
    private volatile boolean serving;
    private boolean webVisible;
    private final Map<String,Map<String,?>> saved=new LinkedHashMap<String,Map<String,?>>();
    private void ui(final Runnable task){final Throwable[] error={null};runOnMainSync(new Runnable(){public void run(){try{task.run();}catch(Throwable e){error[0]=e;}}});if(error[0]!=null)throw new RuntimeException(error[0]);}
    private Object get(Object o,String name){try{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}catch(Exception e){throw new RuntimeException(e);}}
    private void set(Object o,String name,Object value){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new RuntimeException(e);}}
    private void call(Object o,String name){try{Method m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(o);}catch(Exception e){throw new RuntimeException(e);}}
    private SharedPreferences prefs(String name){return getTargetContext().getSharedPreferences(name,0);}
    private void open(Class<?> type){open(new Intent(getTargetContext(),type));}
    private Activity existing(String name){try{Class<?> type=Class.forName("android.app.ActivityThread");Object thread=type.getDeclaredMethod("currentActivityThread").invoke(null);Map<?,?> records=(Map<?,?>)get(thread,"mActivities");for(Object record:records.values()){Activity activity=(Activity)get(record,"activity");if(activity!=null&&activity.getClass().getName().equals(name)&&!activity.isFinishing()&&!activity.isDestroyed())return activity;}return null;}catch(Exception e){throw new RuntimeException(e);}}
    private void open(final Intent intent){
        final String name=intent.getComponent().getClassName();ActivityMonitor monitor=addMonitor(name,null,false);
        try{ui(new Runnable(){public void run(){page=existing(name);getTargetContext().startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}});
            if(page==null)page=waitForMonitorWithTimeout(monitor,8000);
            if(page==null)throw new IllegalStateException("Activity did not open: "+name);
            waitForIdleSync();SystemClock.sleep(600);
        }finally{removeMonitor(monitor);}
    }
    private void close(){if(page!=null){final Activity old=page;ui(new Runnable(){public void run(){old.finish();}});page=null;waitForIdleSync();}}
    private void shot(String name)throws Exception{
        SystemClock.sleep(400);Bitmap image=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();
        if(image==null||image.getWidth()!=240||image.getHeight()!=320)throw new IOException("Expected 240 x 320 capture: "+name);
        try(FileOutputStream out=new FileOutputStream(new File(shots,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
        captures++;Bundle status=new Bundle();status.putString("scene",name);sendStatus(0,status);
    }
    private void snapshot(String name){saved.put(name,new HashMap<String,Object>(prefs(name).getAll()));}
    @SuppressWarnings("unchecked") private void restore(){for(Map.Entry<String,Map<String,?>> group:saved.entrySet()){SharedPreferences.Editor e=prefs(group.getKey()).edit().clear();for(Map.Entry<String,?> row:group.getValue().entrySet()){String k=row.getKey();Object v=row.getValue();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set)e.putStringSet(k,new HashSet<String>((Set<String>)v));}if(!e.commit()||!prefs(group.getKey()).getAll().equals(group.getValue()))throw new IllegalStateException("Preference restore failed: "+group.getKey());}}
    private void fixture()throws Exception{
        prefs("appearance").edit().putInt("theme",17).putInt("accent",-1).putInt(HomeStyle.KEY,0).putBoolean("simple-mode",false).putBoolean("home-compact",false).putBoolean("home-sims",true).putBoolean("home-weather",true).putBoolean("home-unread",true).putBoolean("home-contextual",true).putInt("lock-preview",0).putBoolean("lock-info",true).putBoolean("lock-media",false).commit();
        prefs("accessibility").edit().putInt("text",1).putBoolean("contrast",false).commit();
        prefs(HomeCanvasLayout.PREFERENCES).edit().clear().putInt("7x",0).commit();
        prefs("keyguard").edit().putBoolean("locked",false).commit();prefs("setup").edit().putBoolean("complete",true).commit();prefs("browser").edit().clear().commit();
        JSONObject current=new JSONObject().put("temperature_2m",8).put("apparent_temperature",6).put("relative_humidity_2m",61).put("wind_speed_10m",9).put("weather_code",2);
        JSONObject daily=new JSONObject();
        for(String key:new String[]{"time","temperature_2m_min","temperature_2m_max","precipitation_probability_max","weather_code","sunrise","sunset"}){
            JSONArray values=new JSONArray();for(int i=0;i<7;i++){String date="2026-11-"+(23+i);values.put(key.equals("time")?date:key.equals("sunrise")?date+"T07:20":key.equals("sunset")?date+"T16:45":key.equals("weather_code")?2:key.equals("temperature_2m_min")?3+i%2:key.equals("temperature_2m_max")?8+i%3:20);}daily.put(key,values);
        }
        prefs("weather").edit().putString("city","București").putString("data",new JSONObject().put("current",current).put("daily",daily).put("timezone","Europe/Bucharest").toString()).putLong("updated",System.currentTimeMillis()-5*60000L).commit();
        fixtures=new File(getTargetContext().getFilesDir(),"documents/Showcase-"+UUID.randomUUID());if(!fixtures.mkdirs())throw new IOException("Owned fixture directory");
        new File(fixtures,"Documents").mkdir();new File(fixtures,"Music").mkdir();
        try(FileOutputStream out=new FileOutputStream(new File(fixtures,"Welcome to KeyShell.txt"))){out.write("Local documentation example. No personal files.".getBytes("UTF-8"));}
    }
    private void home()throws Exception{
        for(int style=0;style<3;style++){
            prefs("appearance").edit().putInt(HomeStyle.KEY,style).commit();open(MainActivity.class);final MainActivity.Screen screen=(MainActivity.Screen)get(page,"screen");
            ui(new Runnable(){public void run(){screen.grid=false;screen.reloadAppearance();screen.reloadCards();UnreadState.sms=2;UnreadState.calls=1;screen.cards=new HomeCards.Card[]{new HomeCards.Card(5,"Notificări",HomeContext.unreadLabel(2,1),"")};screen.cardIndex=0;screen.invalidate();}});
            shot("home-"+style);
            if(style==0){ui(new Runnable(){public void run(){screen.grid=true;screen.selected=0;screen.invalidate();}});shot("apps");}
            close();open(KeypadLockActivity.class);ui(new Runnable(){public void run(){UnreadState.sms=2;UnreadState.calls=1;((View)get(page,"screen")).invalidate();}});shot("standby-"+style);
            ui(new Runnable(){public void run(){KeypadLockActivity.unlock(getTargetContext());}});close();
        }
        prefs("appearance").edit().putInt(HomeStyle.KEY,0).commit();
    }
    private void mainPages()throws Exception{
        open(SettingsActivity.class);ui(new Runnable(){public void run(){set(page,"category",1);call(page,"show");}});shot("settings");close();
        open(HomeLayoutActivity.class);shot("home-settings");close();
        open(new Intent(getTargetContext(),ClockActivity.class).putExtra("tab",0));shot("clock");close();
        open(CalculatorActivity.class);ui(new Runnable(){public void run(){set(page,"expression","128+64");call(page,"calculate");((View)get(page,"face")).invalidate();}});shot("calculator");close();
        open(new Intent(getTargetContext(),MessagesActivity.class).putExtra("preview",true));shot("messages-demo");close();
        open(new Intent(getTargetContext(),InCallActivity.class).putExtra("demo",true));shot("call-demo");close();
        open(new Intent(getTargetContext(),MediaActivity.class).putExtra("now",true).putExtra("preview",true));shot("music-demo");close();
        open(StreamRadioActivity.class);ui(new Runnable(){public void run(){set(page,"selected",0);call(page,"showPlayer");}});shot("internet-radio-idle");close();
        open(WeatherActivity.class);shot("weather-current");ui(new Runnable(){public void run(){set(page,"page",1);((View)get(page,"face")).invalidate();}});shot("weather-week");close();
        open(new Intent(getTargetContext(),FilesActivity.class).putExtra("folder",fixtures.getPath()));SystemClock.sleep(700);shot("files-fixture");close();
        gallery();
        open(RecorderActivity.class);shot("recorder-idle");close();
        open(BrowserHubActivity.class);shot("browser-hub");close();
        open(TerminalActivity.class);ui(new Runnable(){public void run(){((EditText)get(page,"command")).setText("uname -m");call(page,"execute");}});SystemClock.sleep(900);shot("terminal");close();
    }
    @SuppressWarnings("unchecked") private void gallery()throws Exception{
        final ArrayList<Uri> samples=new ArrayList<Uri>();String[] assets={"Illus_Peony_Aqua.jpg","Illus_Flow2_Orange.jpg","Illus_Flow2_Sapphire.jpg"};
        for(int i=0;i<4;i++){File image=new File(fixtures,"Gaia-"+(i+1)+".jpg");try(InputStream in=getTargetContext().getAssets().open("wallpapers/gaia/"+assets[i%3]);FileOutputStream out=new FileOutputStream(image)){byte[] data=new byte[8192];int count;while((count=in.read(data))>0)out.write(data,0,count);}samples.add(LeoFileProvider.uri(getTargetContext(),image));}
        open(new Intent(getTargetContext(),GalleryActivity.class).setData(samples.get(0)).putExtra("title","Gaia artwork"));
        ui(new Runnable(){public void run(){List<Uri> images=(List<Uri>)get(page,"images");List<String> names=(List<String>)get(page,"names");images.clear();names.clear();for(int i=0;i<4;i++){images.add(samples.get(i));names.add("Gaia sample "+(i+1));}call(page,"grid");}});SystemClock.sleep(800);shot("gallery-fixture");close();
    }
    private boolean contains(AccessibilityNodeInfo node,String text){if(node==null)return false;CharSequence value=node.getText(),description=node.getContentDescription();if(value!=null&&value.toString().contains(text)||description!=null&&description.toString().contains(text))return true;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child==null)continue;try{if(contains(child,text))return true;}finally{child.recycle();}}return false;}
    private void browser()throws Exception{
        final byte[] html=("<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>KeyShell local demo</title></head><body style='margin:14px;background:#141218;color:#f4eaff;font:15px sans-serif'><small>LOCAL DEMONSTRATION</small><h2 style='color:#d0bcff'>Hello, KeyShell.</h2><p id='ready'>Loading...</p><button style='padding:8px;background:#d0bcff;border:0' onclick=\"this.textContent='OK!'\">D-pad + OK</button><script>document.getElementById('ready').textContent='JavaScript ready';</script></body></html>").getBytes("UTF-8");
        server=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));serving=true;
        Thread worker=new Thread(new Runnable(){public void run(){while(serving)try{Socket socket=server.accept();socket.setSoTimeout(3000);try{BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));String line;while((line=input.readLine())!=null&&!line.isEmpty()){}OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "+html.length+"\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));out.write(html);out.flush();}finally{socket.close();}}catch(IOException end){if(!serving)return;}}});worker.setDaemon(true);worker.start();
        webVisible=true;getTargetContext().startActivity(new Intent(getTargetContext(),WebBrowserActivity.class).setData(Uri.parse("http://127.0.0.1:"+server.getLocalPort()+"/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        long until=SystemClock.elapsedRealtime()+20000;boolean ready=false;
        while(SystemClock.elapsedRealtime()<until){AccessibilityNodeInfo root=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).getRootInActiveWindow();try{ready=contains(root,"KeyShell local demo");}finally{if(root!=null)root.recycle();}if(ready)break;SystemClock.sleep(100);}
        if(!ready)throw new IOException("Local WebView document did not render");
        boolean painted=false;long paintUntil=SystemClock.elapsedRealtime()+15000;
        do{SystemClock.sleep(300);Bitmap frame=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();if(frame!=null){int dark=0;for(int y=120;y<265;y+=5)for(int x=10;x<230;x+=5){int color=frame.getPixel(x,y);if(Color.red(color)<45&&Color.green(color)<45&&Color.blue(color)<55)dark++;}frame.recycle();painted=dark>200;}}while(!painted&&SystemClock.elapsedRealtime()<paintUntil);
        if(!painted)throw new IOException("Local page title loaded, but body was not painted");shot("browser-local");
        closeWeb();
    }
    private void closeWeb(){if(webVisible){getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).performGlobalAction(1);SystemClock.sleep(1000);webVisible=false;}}
    private void remove(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)remove(child);file.delete();}
    public void onCreate(Bundle args){start();}
    public void onStart(){Bundle result=new Bundle();try{
        getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        awake=((PowerManager)getTargetContext().getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"keyshell:documentation");awake.acquire(8*60000L);
        for(String name:new String[]{"appearance","accessibility","weather","keyguard","setup",HomeCanvasLayout.PREFERENCES,"settings-ui","clock","browser","files-ui","gallery"})snapshot(name);
        shots=new File(getTargetContext().getFilesDir(),"showcase-shots");shots.mkdirs();fixture();home();mainPages();browser();
        result.putString("result","CAPTURED "+captures+" documentation scenes; local fixtures only");
    }catch(Throwable error){result.putString("error","after "+captures+" captures: "+android.util.Log.getStackTraceString(error));}finally{
        try{closeWeb();close();serving=false;if(server!=null)server.close();SystemClock.sleep(500);restore();UnreadState.refresh(getTargetContext());if(fixtures!=null)remove(fixtures);if(awake!=null&&awake.isHeld())awake.release();}catch(Throwable error){result.putString("restore_error",android.util.Log.getStackTraceString(error));}
    }finish(result.containsKey("error")||result.containsKey("restore_error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);}
}
