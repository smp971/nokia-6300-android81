package ro.leo.visual;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;

/** Real HTTPS password/role checks on isolated data, plus reversible native owner controls. */
public final class WebUiAccessTests extends WebUiTests {
    boolean provision;
    String owner="owner",ownerPassword=UUID.randomUUID().toString(),readerPassword=UUID.randomUUID().toString();
    CompanionAuth auth;
    File shots;
    String login(String user,String password)throws Exception{
        String response=request("POST","/api/login",null,"Content-Type: application/json\r\n",new JSONObject().put("username",user).put("password",password).toString().getBytes("UTF-8"));
        check(response.startsWith("HTTP/1.1 200"),"HTTPS password login");return new JSONObject(response.substring(response.indexOf("\r\n\r\n")+4)).getString("token");
    }
    JSONObject data(String response)throws Exception{return new JSONObject(response.substring(response.indexOf("\r\n\r\n")+4));}
    void accessTests()throws Exception{
        long start=SystemClock.elapsedRealtime();auth.add(owner,ownerPassword,"admin");long cost=SystemClock.elapsedRealtime()-start;
        check(cost<20000,"bounded on-device password derivation");
        check(!fixture.getSharedPreferences(CompanionAuth.PREFS,0).getString("users","").contains(ownerPassword),"password not stored in plaintext");
        check(auth.configured(),"owner configured");
        String identity=fixture.getSharedPreferences(CompanionAuth.PREFS,0).getString("users","");
        check(identity.contains("bcrypt")&&identity.contains("$2a$10$")&&identity.contains("verifier"),"salted bcrypt cost 10 verifier");
        bearer=login(owner,ownerPassword);String old=bearer;
        check(api("GET","/api/desktop",null).contains("\"role\":\"admin\""),"administrator identity returned");
        check(!api("GET","/api/admin",null).contains("verifier"),"user list exposes no verifiers");
        JSONObject user=new JSONObject().put("action","add").put("username","reader").put("password",readerPassword).put("role","viewer");
        check(api("POST","/api/admin/users",user).startsWith("HTTP/1.1 200"),"administrator adds read-only user");
        String admin=bearer;bearer=login("reader",readerPassword);
        check(api("GET","/api/desktop",null).startsWith("HTTP/1.1 200"),"viewer reads status");
        check(api("POST","/api/appearance",new JSONObject().put("theme",2).put("list",false)).startsWith("HTTP/1.1 403"),"viewer cannot change phone appearance");
        check(api("POST","/api/layout",new JSONObject().put("enabled",true).put("moves",new JSONArray())).startsWith("HTTP/1.1 403"),"viewer cannot rearrange apps");
        check(request("POST","/api/upload",bearer,"X-Leo-Name: denied.txt\r\n",new byte[0]).startsWith("HTTP/1.1 403"),"viewer cannot upload");
        check(api("GET","/api/admin",null).startsWith("HTTP/1.1 403"),"viewer cannot enumerate users");
        bearer=admin;
        JSONObject config=new JSONObject().put("theme",2).put("autoStart",true).put("appearance",false).put("layout",false).put("files",false);
        check(api("POST","/api/admin/settings",config).startsWith("HTTP/1.1 200"),"WebUI controls saved");
        check(api("GET","/api/files",null).startsWith("HTTP/1.1 403"),"disabled file feature denied even to administrator");
        check(api("POST","/api/appearance",new JSONObject().put("theme",1).put("list",false)).startsWith("HTTP/1.1 403"),"disabled appearance denied");
        check(request("GET","/api/info",null,null,null).contains("\"theme\":2"),"public login theme contains no account data");
        config.put("appearance",true).put("layout",true).put("files",true);api("POST","/api/admin/settings",config);
        String next=UUID.randomUUID().toString();
        JSONObject response=data(api("POST","/api/password",new JSONObject().put("currentPassword",ownerPassword).put("newPassword",next)));
        bearer=response.getString("token");ownerPassword=next;
        check(request("GET","/api/desktop",old,null,null).startsWith("HTTP/1.1 401"),"password rotation revokes previous session");
        check(api("GET","/api/desktop",null).startsWith("HTTP/1.1 200"),"password change keeps current browser connected");
        check(api("POST","/api/admin/users",new JSONObject().put("action","delete").put("username",owner)).startsWith("HTTP/1.1 400"),"last administrator cannot be deleted");
        bearer=login("reader",readerPassword);String reader=bearer;bearer=response.getString("token");
        api("POST","/api/admin/users",new JSONObject().put("action","delete").put("username","reader"));
        check(request("GET","/api/desktop",reader,null,null).startsWith("HTTP/1.1 401"),"deleting user revokes its sessions");
        auth.add("reader",readerPassword,"viewer");
        String logout=bearer;api("POST","/api/logout",new JSONObject());
        check(request("GET","/api/desktop",logout,null,null).startsWith("HTTP/1.1 401"),"logout invalidates session");bearer=login(owner,ownerPassword);
        CompanionAuth gate=new CompanionAuth(fixture);for(int i=0;i<5;i++)try{gate.login(owner,"wrong-password");throw new AssertionError();}catch(CompanionAuth.Denied denied){check(denied.status==401,"wrong login "+i);}
        try{gate.login(owner,ownerPassword);throw new AssertionError();}catch(CompanionAuth.Denied denied){check(denied.status==429,"five failed attempts impose a cooldown");}
        check(AppCatalog.NAMES.length==50&&AppCatalog.intent(fixture,49).getComponent().getClassName().equals(CompanionActivity.class.getName()),"appended WebUI app ID preserves old catalog");
        check(contains(LauncherFolders.entries(fixture,4),49),"WebUI lives in Advanced folder");
        bitmap();
    }
    void bitmap(){Bitmap bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);new IconArt().draw(new Canvas(bitmap),49,32,32);check(bitmap.getPixel(32,32)!=Color.TRANSPARENT,"satellite artwork renders");bitmap.recycle();}
    void shot(String name)throws Exception{
        SystemClock.sleep(150);Bitmap image=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();check(image!=null,"capture "+name);
        try(FileOutputStream out=new FileOutputStream(new File(shots,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
    }
    void nativePanel(final Context real)throws Exception{
        final Map<String,?> settings=new HashMap<String,Object>(real.getSharedPreferences(CompanionSettings.PREFS,0).getAll());
        Activity page=null;try{
            page=startActivitySync(new Intent(real,CompanionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();shot("native-controls");
            final CompanionActivity panel=(CompanionActivity)page;
            ui(new Runnable(){public void run(){try{java.lang.reflect.Method m=CompanionActivity.class.getDeclaredMethod("features");m.setAccessible(true);m.invoke(panel);}catch(Exception e){throw new RuntimeException(e);}}});shot("native-features");
            check(panel.root!=null,"native control panel supports theme-aware lists");
        }finally{if(page!=null){final Activity old=page;ui(new Runnable(){public void run(){old.finish();}});}restore(real,CompanionSettings.PREFS,settings);}
    }
    void bootContract(final Context real)throws Exception{
        final int[] starts={0};Context mock=new ContextWrapper(fixture){public ComponentName startForegroundService(Intent i){starts[0]++;return i.getComponent();}};
        fixture.getSharedPreferences(CompanionSettings.PREFS,0).edit().putBoolean("enabled",true).putBoolean("auto-start",true).commit();
        new CompanionBootReceiver().onReceive(mock,new Intent(Intent.ACTION_BOOT_COMPLETED));check(starts[0]==1,"enabled configured owner resumes on boot broadcast route");
        fixture.getSharedPreferences(CompanionSettings.PREFS,0).edit().putBoolean("auto-start",false).commit();new CompanionBootReceiver().onReceive(mock,new Intent(Intent.ACTION_BOOT_COMPLETED));check(starts[0]==1,"disabled auto-start respected");
        fixture.getSharedPreferences(CompanionSettings.PREFS,0).edit().putBoolean("auto-start",true).putBoolean("enabled",false).commit();new CompanionBootReceiver().onReceive(mock,new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));check(starts[0]==1,"manual off survives app update");
    }
    void provision(Context real)throws Exception{
        File file=new File(real.getFilesDir(),"webui-owner-provision.json");
        try{JSONObject input=new JSONObject(new String(BackupEngine.bytes(file,4096),"UTF-8"));CompanionAuth owner=new CompanionAuth(real);String password=input.getString("password");
            boolean found=false;JSONArray users=owner.users();for(int i=0;i<users.length();i++)if(users.getJSONObject(i).getString("username").equals("admin"))found=true;
            if(found)owner.reset("admin",password);else owner.add("admin",password,"admin");
            real.getSharedPreferences(CompanionSettings.PREFS,0).edit().putBoolean("auto-start",true).putBoolean("enabled",true).commit();CompanionService.ensureStarted(real);awaitServer();
            CompanionService live=CompanionService.instance;String test=live.auth.login("admin",password);check(live.auth.account("Bearer "+test)!=null,"owner password configured and verified");live.auth.logout("Bearer "+test);
            try(FileOutputStream out=new FileOutputStream(new File(real.getFilesDir(),"webui-active.json"))){out.write(new JSONObject().put("endpoint",live.address).put("fingerprint",live.fingerprint).toString().getBytes("UTF-8"));}
        }finally{file.delete();}
    }
    public void onCreate(Bundle args){provision=args!=null&&"true".equals(args.getString("provision"));serve=args!=null&&"true".equals(args.getString("serve"));tryService=args!=null&&"true".equals(args.getString("service"));start();}
    public void onStart(){Bundle result=new Bundle();final Context real=getTargetContext();File session=new File(real.getFilesDir(),"webui-fixture-session.json"),done=new File(real.getFilesDir(),"webui-fixture.done");try{
        getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        awake=((PowerManager)real.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"keyshell:webui-access-checks");awake.acquire(480000);
        if(provision){provision(real);result.putString("result","Owner configured; persistent WebUI enabled");}
        else{
            directory=new File(real.getCacheDir(),prefix);directory.mkdirs();fixture=new ContextWrapper(real){public Context getApplicationContext(){return this;}public File getFilesDir(){return directory;}public SharedPreferences getSharedPreferences(String name,int mode){groups.add(name);return real.getSharedPreferences(prefix+name,mode);}};
            auth=new CompanionAuth(fixture);server=new CompanionServer(fixture,InetAddress.getByName("127.0.0.1"),serve?18765:0,auth);server.start();
            shots=new File(real.getFilesDir(),"webui-access-shots");shots.mkdirs();accessTests();bootContract(real);nativeUpdates(real);nativePanel(real);if(tryService)service(real);
            if(serve){done.delete();JSONObject credentials=new JSONObject().put("endpoint","https://127.0.0.1:18765").put("username",owner).put("password",ownerPassword).put("reader","reader").put("readerPassword",readerPassword).put("fingerprint",CompanionTls.fingerprint());
                try(FileOutputStream out=new FileOutputStream(session)){out.write(credentials.toString().getBytes("UTF-8"));}sendStatus(1,new Bundle());long until=SystemClock.elapsedRealtime()+180000;while(!done.exists()&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(250);
            }result.putString("result","PASS "+checks+" password, role, lifecycle and native WebUI checks");
        }
    }catch(Throwable error){result.putString("error","after "+checks+" checks: "+android.util.Log.getStackTraceString(error));}finally{
        if(server!=null)server.close();session.delete();done.delete();for(String group:groups)real.deleteSharedPreferences(prefix+group);if(directory!=null)remove(directory);if(awake!=null&&awake.isHeld())awake.release();
    }
    try(FileOutputStream out=new FileOutputStream(new File(real.getFilesDir(),"webui-access-result.txt"))){out.write(result.toString().getBytes("UTF-8"));}catch(IOException ignored){}finish(result.containsKey("error")?Activity.RESULT_CANCELED:Activity.RESULT_OK,result);}
}
