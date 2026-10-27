package ro.leo.visual;
import android.app.*;import android.content.*;import android.content.pm.PackageManager;
import android.database.Cursor;import android.net.Uri;import android.os.*;import android.view.*;
import java.util.*;import java.net.*;import java.io.*;

/** Local fixtures only: no calls, SMS, audio or external download servers. */
public final class DailyToolsTests extends Instrumentation {
    private int checks;private Activity page;
    private void check(boolean yes,String message){if(!yes)throw new AssertionError(message);checks++;}
    private void close(){if(page!=null){final Activity old=page;runOnMainSync(new Runnable(){public void run(){old.finish();}});page=null;waitForIdleSync();}}
    private Activity open(Class<?> type){close();page=startActivitySync(new Intent(getTargetContext(),type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();return page;}
    private int step()throws Exception{java.lang.reflect.Field f=SetupWizardActivity.class.getDeclaredField("step");f.setAccessible(true);return f.getInt(page);}
    private void restore(SharedPreferences p,Map<String,?> saved){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> v:saved.entrySet()){if(v.getValue() instanceof Boolean)e.putBoolean(v.getKey(),(Boolean)v.getValue());else if(v.getValue() instanceof Integer)e.putInt(v.getKey(),(Integer)v.getValue());else if(v.getValue() instanceof Long)e.putLong(v.getKey(),(Long)v.getValue());else if(v.getValue() instanceof String)e.putString(v.getKey(),(String)v.getValue());}e.commit();}
    public void onCreate(Bundle b){start();}
    public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();SharedPreferences setup=c.getSharedPreferences("setup",0),access=c.getSharedPreferences("accessibility",0),fixture=c.getSharedPreferences("schedule-test-fixture",0);Map<String,?> savedSetup=new HashMap<>(setup.getAll()),savedAccess=new HashMap<>(access.getAll());
        try{
            check(!NavigationPace.reject(0,20,2000,10),"normal mode keeps system repeat");
            check(!NavigationPace.reject(2,0,0,0),"fresh press is never blocked");
            check(NavigationPace.reject(1,1,300,300),"slow mode delays held repeat");
            check(NavigationPace.reject(1,2,900,100),"slow mode limits repeat rate");
            check(!NavigationPace.reject(1,3,900,200),"slow mode permits spaced repeats");
            check(NavigationPace.reject(2,99,5000,5000),"one-press mode blocks repeats");
            NavigationPace pace=new NavigationPace();access.edit().putInt("repeat",2).commit();
            check(!pace.consume(c,new KeyEvent(100,100,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN,0)),"initial arrow passes");
            check(pace.consume(c,new KeyEvent(100,700,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN,1)),"held arrow is consumed");
            check(!pace.consume(c,new KeyEvent(100,710,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_DOWN,0)),"release passes");
            check(!pace.consume(c,new KeyEvent(100,800,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_CALL,1)),"call key untouched");
            restore(access,savedAccess);
            GlobalSearchActivity.Hit wifi=new GlobalSearchActivity.Hit(ro.leo.i18n.L.t("Setare · ")+"Wi-Fi",new Intent(c,ConnectivityActivity.class));
            check(GlobalSearchActivity.inScope(wifi,3)&&!GlobalSearchActivity.inScope(wifi,4),"settings scope isolates connectivity");
            check(GlobalSearchActivity.inScope(new GlobalSearchActivity.Hit("Music",new Intent(c,MediaActivity.class)),4),"music scope");
            boolean found=false;for(GlobalSearchActivity.Hit hit:GlobalSearchActivity.collect(c,"Wi-Fi",3))if(GlobalSearchActivity.inScope(hit,3))found=true;
            check(found,"global search includes Wi-Fi settings route");
            check(SetupWizardActivity.boundedStep(-4)==0&&SetupWizardActivity.boundedStep(99)==9,"wizard clamps persisted position");
            setup.edit().putInt("step",3).commit();open(SetupWizardActivity.class);check(step()==3,"wizard resumes saved SIM step");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();check(step()==4,"OK advances without changing SIM");
            close();open(SetupWizardActivity.class);check(step()==4,"wizard persists progress across activity restart");close();restore(setup,savedSetup);
            fixture.edit().clear().putBoolean("enabled",false).commit();check(SoundScheduleActivity.boundary(fixture).equals(ro.leo.i18n.L.t("Programul este oprit")),"disabled schedule has no fake countdown");
            fixture.edit().putBoolean("enabled",true).putLong("skip-until",System.currentTimeMillis()+60000).commit();check(SoundScheduleActivity.boundary(fixture).startsWith(ro.leo.i18n.L.t("Suspendat manual până la ")),"manual override is visible");
            check(DownloadsActivity.validUrl("https://example.org/file")&&!DownloadsActivity.validUrl("file:///etc/passwd")&&!DownloadsActivity.validUrl("https://user:pass@example.org/file"),"download URL restrictions");
            check("25%".equals(DownloadsActivity.progress(25,100))&&"100%".equals(DownloadsActivity.progress(200,100))&&"0 B".equals(DownloadsActivity.progress(-1,-1)),"progress is bounded and handles unknown size");
            download(c);
            open(RecentAppsActivity.class);SystemClock.sleep(1500);waitForIdleSync();check(!page.isFinishing(),"recent apps opens with media shortcut");close();
            open(AccessibilitySettingsActivity.class);check(!page.isFinishing(),"repeat settings opens");close();
            check(!c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC),"this phone reports no NFC hardware feature");
            result.putString("result","PASS "+checks+" daily tools checks");
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{close();restore(setup,savedSetup);restore(access,savedAccess);fixture.edit().clear().commit();}
        finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);
    }
    private void download(Context c)throws Exception{
        final byte[] payload="Leo local download fixture\n".getBytes("UTF-8");
        final ServerSocket server=new ServerSocket(0,2,InetAddress.getByName("127.0.0.1"));server.setSoTimeout(15000);
        Thread responder=new Thread(new Runnable(){public void run(){try{for(int attempt=0;attempt<2;attempt++)try(Socket s=server.accept()){s.setSoTimeout(3000);BufferedReader in=new BufferedReader(new InputStreamReader(s.getInputStream(),"US-ASCII"));String line;while((line=in.readLine())!=null&&!line.isEmpty()){}OutputStream out=s.getOutputStream();boolean success=attempt==1;byte[] body=success?payload:new byte[0];out.write(((success?"HTTP/1.1 200 OK":"HTTP/1.1 404 Not Found")+"\r\nContent-Type: text/plain\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));out.write(body);out.flush();}}catch(Exception ignored){}finally{try{server.close();}catch(Exception ignored){}}}},"LeoLocalDownloadFixture");responder.start();
        DownloadManager manager=(DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE);ArrayList<Long> ids=new ArrayList<>();
        try{String url="http://127.0.0.1:"+server.getLocalPort()+"/leo-local-fixture.txt";
            long failed=manager.enqueue(DownloadsActivity.request(url,false));ids.add(failed);check(await(manager,failed)==DownloadManager.STATUS_FAILED,"DownloadManager reports local HTTP failure");
            long retry=manager.enqueue(DownloadsActivity.request(url,false));ids.add(retry);check(await(manager,retry)==DownloadManager.STATUS_SUCCESSFUL,"same request builder retries into a successful transfer");
            Uri uri=manager.getUriForDownloadedFile(retry);try(InputStream in=c.getContentResolver().openInputStream(uri)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[256];int n;while((n=in.read(b))>0)out.write(b,0,n);check(Arrays.equals(payload,out.toByteArray()),"downloaded fixture content matches");}
        }finally{for(long id:ids)manager.remove(id);server.close();responder.join(3500);}
    }
    private int await(DownloadManager manager,long id)throws Exception{long until=SystemClock.uptimeMillis()+20000;int state=0;while(SystemClock.uptimeMillis()<until){try(Cursor r=manager.query(new DownloadManager.Query().setFilterById(id))){if(r!=null&&r.moveToFirst())state=r.getInt(r.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));}if(state==DownloadManager.STATUS_SUCCESSFUL||state==DownloadManager.STATUS_FAILED)return state;SystemClock.sleep(250);}return state;}
}
