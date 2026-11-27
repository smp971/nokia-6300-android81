package ro.leo.visual;
import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import org.json.*;

/** Isolated TLS loopback and settings tests. Optional short browser fixture; never exposes production files. */
public class WebUiTests extends Instrumentation {
    int checks;String bearer;boolean serve,tryService;CompanionServer server;Context fixture;File directory;PowerManager.WakeLock awake;volatile MainActivity home;
    final String prefix="webui-check-"+UUID.randomUUID()+"-";final Set<String> groups=new HashSet<String>();
    void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    SSLSocket socket()throws Exception{
        final byte[] expected=CompanionTls.keys().getCertificate(CompanionTls.ALIAS).getEncoded();
        SSLContext tls=SSLContext.getInstance("TLS");tls.init(null,new TrustManager[]{new X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] chain,String auth)throws java.security.cert.CertificateException{throw new java.security.cert.CertificateException();}
            public void checkServerTrusted(X509Certificate[] chain,String auth)throws java.security.cert.CertificateException{try{if(chain.length!=1||!MessageDigest.isEqual(expected,chain[0].getEncoded()))throw new java.security.cert.CertificateException("Wrong fixture identity");chain[0].checkValidity();}catch(Exception e){throw new java.security.cert.CertificateException(e);}}
        }},new SecureRandom());
        SSLSocket socket=(SSLSocket)tls.getSocketFactory().createSocket(server.server.getInetAddress().getHostAddress(),server.server.getLocalPort());socket.setSoTimeout(10000);socket.startHandshake();return socket;
    }
    String request(String method,String path,String auth,String extra,byte[] body)throws Exception{
        try(SSLSocket socket=socket()){
            String headers=method+" "+path+" HTTP/1.1\r\nHost: "+server.server.getInetAddress().getHostAddress()+":"+server.server.getLocalPort()+"\r\n"+(auth==null?"":"Authorization: Bearer "+auth+"\r\n")+(extra==null?"":extra)+(body==null?"":"Content-Length: "+body.length+"\r\n")+"\r\n";
            OutputStream out=socket.getOutputStream();out.write(headers.getBytes("US-ASCII"));if(body!=null)out.write(body);out.flush();
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] data=new byte[8192];int n;while((n=socket.getInputStream().read(data))!=-1){bytes.write(data,0,n);if(bytes.size()>262144)throw new IOException("Response too large");
                String response=bytes.toString("ISO-8859-1");int split=response.indexOf("\r\n\r\n");if(split>=0){java.util.regex.Matcher length=java.util.regex.Pattern.compile("Content-Length: ([0-9]+)").matcher(response.substring(0,split));if(length.find()&&bytes.size()>=split+4+Integer.parseInt(length.group(1)))break;}
            }return bytes.toString("UTF-8");
        }
    }
    String api(String method,String path,JSONObject data)throws Exception{return request(method,path,server.auth==null?server.token:bearer,data==null?null:"Content-Type: application/json\r\n",data==null?null:data.toString().getBytes("UTF-8"));}
    void tests()throws Exception{
        try(SSLSocket socket=socket()){check(socket.getSession().getProtocol().equals("TLSv1.2"),"TLS 1.2 negotiated");}
        check(CompanionTls.fingerprint().matches("([A-F0-9]{2}:){31}[A-F0-9]{2}"),"visible SHA-256 identity");
        check(request("GET","/",null,null,null).contains("KeyShell"),"public WebUI served");
        for(String path:new String[]{"/style.css","/app.js","/catalog-icons/gaia/phone.png"})check(request("GET",path,null,null,null).startsWith("HTTP/1.1 200"),"asset served "+path);
        check(request("GET","/api/desktop",null,null,null).startsWith("HTTP/1.1 401"),"API requires token");
        check(request("GET","/api/desktop","wrong",null,null).startsWith("HTTP/1.1 401"),"bad token rejected");
        check(request("GET","/api/desktop",server.token,"Origin: https://evil.invalid\r\n",null).startsWith("HTTP/1.1 403"),"cross-origin rejected");
        check(request("GET","/api/desktop",server.token,"Origin: http://127.0.0.1:"+server.server.getLocalPort()+"\r\n",null).startsWith("HTTP/1.1 403"),"downgraded origin rejected");
        JSONObject before=CompanionModel.snapshot(fixture);check(before.getJSONArray("apps").length()==36&&before.getJSONArray("folders").length()==5,"native catalog projection complete");
        check(api("GET","/api/desktop",null).contains("ram_free"),"live device model");
        JSONObject appearance=new JSONObject().put("theme",17).put("list",true);
        check(api("POST","/api/appearance",appearance).startsWith("HTTP/1.1 200")&&fixture.getSharedPreferences("appearance",0).getBoolean("list",false),"appearance persisted through HTTPS");
        appearance.put("theme",999);check(api("POST","/api/appearance",appearance).startsWith("HTTP/1.1 400")&&fixture.getSharedPreferences("appearance",0).getInt("theme",-1)==17,"invalid theme rejected before mutation");
        appearance.put("theme",1).put("extra",0);check(api("POST","/api/appearance",appearance).startsWith("HTTP/1.1 400"),"unknown preference rejected");
        JSONObject layout=new JSONObject().put("enabled",true).put("moves",new JSONArray().put(new JSONObject().put("id",4).put("folder",1)));
        check(api("POST","/api/layout",layout).startsWith("HTTP/1.1 200")&&LauncherFolders.folder(fixture,4)==1,"folder move persisted");
        layout.getJSONArray("moves").put(new JSONObject().put("id",7).put("folder",0));
        check(api("POST","/api/layout",layout).startsWith("HTTP/1.1 400")&&LauncherFolders.folder(fixture,4)==1,"invalid batch rejected as a whole");
        check(request("GET","/api/file/..%2Fsecret",server.token,null,null).startsWith("HTTP/1.1 400"),"traversal rejected");
        check(api("POST","/api/shell",new JSONObject()).startsWith("HTTP/1.1 404"),"no remote shell endpoint");
        final CountDownLatch ready=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            Callable<String> upload=new Callable<String>(){public String call()throws Exception{ready.await();return request("POST","/api/upload",server.token,"X-Leo-Name: race.txt\r\n",new byte[262144]);}};
            Future<String> first=pool.submit(upload),second=pool.submit(upload);ready.countDown();String a=first.get(25,TimeUnit.SECONDS),b=second.get(25,TimeUnit.SECONDS);
            check(a.startsWith("HTTP/1.1 201")&&b.startsWith("HTTP/1.1 409")||b.startsWith("HTTP/1.1 201")&&a.startsWith("HTTP/1.1 409"),"concurrent same-name uploads cannot replace each other");
            check(new File(CompanionServer.folder(fixture),"race.txt").length()==262144,"complete upload published once");
        }finally{pool.shutdownNow();}
        byte[] payload="KeyShell isolated browser fixture\n".getBytes("UTF-8");
        check(request("POST","/api/upload",server.token,"X-Leo-Name: webui-fixture.txt\r\n",payload).startsWith("HTTP/1.1 201"),"fixture upload");
        check(request("GET","/api/file/webui-fixture.txt",server.token,null,null).endsWith(new String(payload,"UTF-8")),"download byte match");
        for(File file:CompanionServer.folder(fixture).listFiles())check(!file.getName().startsWith(".upload-"),"no staging files left");
        fixture.getSharedPreferences("appearance",0).edit().clear().putInt("theme",17).commit();fixture.getSharedPreferences(LauncherFolders.PREFERENCES,0).edit().clear().commit();
    }
    void remove(File f){File[] children=f.listFiles();if(children!=null)for(File child:children)remove(child);f.delete();}
    void ui(final Runnable action){final Throwable[] error={null};runOnMainSync(new Runnable(){public void run(){try{action.run();}catch(Throwable e){error[0]=e;}}});if(error[0]!=null)throw new RuntimeException(error[0]);waitForIdleSync();}
    @Override public void callActivityOnResume(Activity page){super.callActivityOnResume(page);if(page instanceof MainActivity)home=(MainActivity)page;}
    MainActivity.Screen screen(){try{java.lang.reflect.Field f=MainActivity.class.getDeclaredField("screen");f.setAccessible(true);return (MainActivity.Screen)f.get(home);}catch(Exception e){throw new RuntimeException(e);}}
    @SuppressWarnings("unchecked") void restore(Context real,String name,Map<String,?> values){SharedPreferences.Editor edit=real.getSharedPreferences(name,0).edit().clear();for(Map.Entry<String,?> row:values.entrySet()){String key=row.getKey();Object value=row.getValue();if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Set)edit.putStringSet(key,new HashSet<String>((Set<String>)value));}check(edit.commit(),"restore "+name);}
    void nativeUpdates(final Context real)throws Exception{
        Map<String,?> appearance=new HashMap<String,Object>(real.getSharedPreferences("appearance",0).getAll()),folders=new HashMap<String,Object>(real.getSharedPreferences(LauncherFolders.PREFERENCES,0).getAll());
        try{
            ui(new Runnable(){public void run(){real.startActivity(new Intent(real,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}});
            for(int i=0;i<100&&home==null;i++)SystemClock.sleep(100);check(home!=null,"bounded native Home launch");
            ui(new Runnable(){public void run(){real.getSharedPreferences("appearance",0).edit().putBoolean("simple-mode",false).commit();screen().folder=-1;screen().reloadAppearance();screen().grid=true;}});
            check(screen().folder==-1,"native fixture starts at root");
            CompanionModel.layout(real,new JSONObject().put("enabled",true).put("moves",new JSONArray().put(new JSONObject().put("id",4).put("folder",-1))));
            for(int i=0;i<50&&!contains(screen().order,4);i++){SystemClock.sleep(40);waitForIdleSync();}check(contains(screen().order,4),"web folder write refreshes visible Home without restart");
            CompanionModel.appearance(real,new JSONObject().put("theme",2).put("list",false));waitForIdleSync();
            check(screen().palette.background==HomeStyle.palette(real,Appearance.palette(real)).background&&!screen().list,"web appearance write refreshes native Canvas");
        }finally{restore(real,"appearance",appearance);restore(real,LauncherFolders.PREFERENCES,folders);if(home!=null)ui(new Runnable(){public void run(){screen().grid=false;screen().reloadAppearance();}});}
    }
    boolean contains(int[] values,int id){for(int value:values)if(value==id)return true;return false;}
    void service(final Context real)throws Exception{
        if(CompanionService.instance!=null)throw new IllegalStateException("Existing WebUI left untouched");
        Map<String,?> prefs=new HashMap<String,Object>(real.getSharedPreferences(CompanionSettings.PREFS,0).getAll()),credentials=new HashMap<String,Object>(real.getSharedPreferences(CompanionAuth.PREFS,0).getAll());
        CompanionServer loopback=server;String oldBearer=bearer;
        String user="check-"+UUID.randomUUID().toString().substring(0,8),password=UUID.randomUUID().toString();
        try{
            new CompanionAuth(real).add(user,password,"admin");real.getSharedPreferences(CompanionSettings.PREFS,0).edit().putBoolean("enabled",true).commit();
            real.startForegroundService(new Intent(real,CompanionService.class));awaitServer();
            CompanionService live=CompanionService.instance;check(live!=null&&live.server!=null,"persistent Wi-Fi service starts");server=live.server;bearer=live.auth.login(user,password);
            check(server.scheme.equals("https")&&!server.server.getInetAddress().isLoopbackAddress(),"production Wi-Fi uses password HTTPS");
            check(api("GET","/api/status",null).contains("KeyShell"),"password session works on Wi-Fi binding");
            check(CompanionService.active(),"satellite active only with a listening server");
            final CompanionService current=live;
            ui(new Runnable(){public void run(){try{java.lang.reflect.Method m=CompanionService.class.getDeclaredMethod("bindTo",InetAddress.class);m.setAccessible(true);m.invoke(current,new Object[]{null});}catch(Exception e){throw new RuntimeException(e);}}});
            check(CompanionService.instance==current&&!CompanionService.active()&&current.address.isEmpty(),"Wi-Fi wait keeps service and hides satellite");
            ui(new Runnable(){public void run(){try{java.lang.reflect.Method m=CompanionService.class.getDeclaredMethod("refreshBinding");m.setAccessible(true);m.invoke(current);}catch(Exception e){throw new RuntimeException(e);}}});awaitServer();server=current.server;
            check(CompanionService.active()&&api("GET","/api/status",null).contains("KeyShell"),"rebinding restores satellite and browser session");
            real.stopService(new Intent(real,CompanionService.class));awaitStopped();check(CompanionSettings.enabled(real),"unexpected service stop retains owner preference");
            CompanionService.ensureStarted(real);awaitServer();check(CompanionService.active(),"enabled service can resume automatically");
            CompanionSettings.enabled(real,false);awaitStopped();check(!CompanionService.active(),"explicit off stops service and satellite");
            real.getSharedPreferences(CompanionSettings.PREFS,0).edit().putBoolean("enabled",true).commit();real.startForegroundService(new Intent(real,CompanionService.class));SystemClock.sleep(20);real.stopService(new Intent(real,CompanionService.class));SystemClock.sleep(600);waitForIdleSync();
            check(CompanionService.instance==null,"immediate stop cannot publish late server");
            try(java.net.ServerSocket probe=new java.net.ServerSocket()){probe.bind(new InetSocketAddress(CompanionService.wifi(real),18764));check(true,"pending TLS listener closes");}
        }finally{CompanionSettings.enabled(real,false);awaitStopped();restore(real,CompanionSettings.PREFS,prefs);restore(real,CompanionAuth.PREFS,credentials);server=loopback;bearer=oldBearer;}
    }
    void awaitServer(){for(int i=0;i<150&&!CompanionService.active();i++)SystemClock.sleep(100);check(CompanionService.active(),"bounded WebUI bind");}
    void awaitStopped(){for(int i=0;i<100&&CompanionService.instance!=null;i++)SystemClock.sleep(50);check(CompanionService.instance==null,"bounded WebUI stop");}
    public void onCreate(Bundle args){serve=args!=null&&"true".equals(args.getString("serve"));tryService=args!=null&&"true".equals(args.getString("service"));start();}
    public void onStart(){Bundle result=new Bundle();final Context real=getTargetContext();File session=new File(real.getFilesDir(),"webui-fixture-session.json"),done=new File(real.getFilesDir(),"webui-fixture.done");try{
        getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);awake=((PowerManager)real.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"keyshell:webui-checks");awake.acquire(240000);
        directory=new File(real.getCacheDir(),prefix);directory.mkdirs();fixture=new ContextWrapper(real){public Context getApplicationContext(){return this;}public File getFilesDir(){return directory;}public SharedPreferences getSharedPreferences(String name,int mode){groups.add(name);return real.getSharedPreferences(prefix+name,mode);}};
        server=new CompanionServer(fixture,InetAddress.getByName("127.0.0.1"),serve?18765:0,CompanionServer.token(),true);server.start();tests();nativeUpdates(real);if(tryService)service(real);
        if(serve){done.delete();JSONObject credentials=new JSONObject().put("endpoint","https://127.0.0.1:18765").put("token",server.token).put("fingerprint",CompanionTls.fingerprint());
            try(FileOutputStream out=new FileOutputStream(session)){out.write(credentials.toString().getBytes("UTF-8"));}sendStatus(1,new Bundle());
            long until=SystemClock.elapsedRealtime()+150000;while(!done.exists()&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(250);
        }result.putString("result","PASS "+checks+" isolated WebUI HTTPS checks");
    }catch(Throwable error){result.putString("error","after "+checks+" checks: "+android.util.Log.getStackTraceString(error));}finally{
        if(server!=null)server.close();session.delete();done.delete();for(String name:groups)real.deleteSharedPreferences(prefix+name);if(directory!=null)remove(directory);if(awake!=null&&awake.isHeld())awake.release();
    }
    try(FileOutputStream out=new FileOutputStream(new File(real.getFilesDir(),"webui-result.txt"))){out.write(result.toString().getBytes("UTF-8"));}catch(IOException ignored){}finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);}
}
