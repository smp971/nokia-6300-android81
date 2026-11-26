package ro.leo.visual;
import android.content.Context;
import android.os.SystemClock;
import java.io.IOException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.jcraft.jsch.jbcrypt.BCrypt;
import org.json.*;

/** Phone-local bcrypt verifiers; hashing never holds the preference-store lock. */
final class CompanionAuth {
    static final String PREFS="companion-auth";
    static final int COST=10;
    static final long SESSION_MS=12*60*60*1000L;
    private static final Object STORE=new Object();
    // A zero-salt dummy verifier equalizes unknown-user work; it never grants access.
    private static final String DUMMY="$2a$10$.....................................................";
    final Context context;
    private final LinkedHashMap<String,Session> sessions=new LinkedHashMap<String,Session>();
    private int failures;private long blockedUntil;private volatile boolean closed;
    static final class Session {
        final String username,version;final long until;
        Session(String username,String version){this.username=username;this.version=version;until=SystemClock.elapsedRealtime()+SESSION_MS;}
    }
    static final class Denied extends Exception {final int status;Denied(int status){this.status=status;}}
    CompanionAuth(Context c){context=c.getApplicationContext();}
    static boolean username(String value){return value!=null&&value.matches("[a-z0-9][a-z0-9._-]{0,31}");}
    static void password(String value){if(value==null||value.length()<8||value.getBytes(StandardCharsets.UTF_8).length>72||value.indexOf(0)>=0)throw new IllegalArgumentException("Password must have at least 8 characters and at most 72 UTF-8 bytes");}
    private JSONArray read()throws Exception{return new JSONArray(context.getSharedPreferences(PREFS,0).getString("users","[]"));}
    private JSONObject find(JSONArray users,String name)throws Exception{for(int i=0;i<users.length();i++)if(users.getJSONObject(i).getString("username").equals(name))return users.getJSONObject(i);return null;}
    private JSONObject current(String name)throws Exception{synchronized(STORE){return find(read(),name);}}
    boolean configured(){synchronized(STORE){try{return read().length()>0;}catch(Exception e){return false;}}}
    private boolean matches(JSONObject user,String value)throws Exception{
        if(value==null||value.getBytes(StandardCharsets.UTF_8).length>72||value.indexOf(0)>=0)return false;
        String verifier=user==null?DUMMY:user.getString("verifier");
        if(!verifier.matches("\\$2a\\$(10|11|12)\\$[./A-Za-z0-9]{53}"))return false;
        return BCrypt.checkpw(value,verifier)&&user!=null;
    }
    private JSONObject record(String name,String value,String role)throws Exception{
        if(!username(name)||!(role.equals("admin")||role.equals("viewer")))throw new IllegalArgumentException("Invalid user");password(value);
        String verifier=BCrypt.hashpw(value,BCrypt.gensalt(COST,new SecureRandom()));
        return new JSONObject().put("username",name).put("role",role).put("algorithm","bcrypt").put("verifier",verifier).put("version",UUID.randomUUID().toString());
    }
    private void save(JSONArray users)throws IOException{if(closed||!context.getSharedPreferences(PREFS,0).edit().putString("users",users.toString()).commit())throw new IOException("Users not saved");}
    void add(String name,String value,String role)throws Exception{
        synchronized(STORE){JSONArray users=read();if(users.length()>=8||find(users,name)!=null)throw new IllegalArgumentException("User exists or limit reached");}
        JSONObject next=record(name,value,role);
        synchronized(STORE){JSONArray users=read();if(users.length()>=8||find(users,name)!=null)throw new IllegalArgumentException("User exists or limit reached");if(users.length()==0&&!role.equals("admin"))throw new IllegalArgumentException("First user must be admin");users.put(next);save(users);}
    }
    private void replace(JSONArray users,String name,JSONObject next)throws Exception{for(int i=0;i<users.length();i++)if(users.getJSONObject(i).getString("username").equals(name)){users.put(i,next);save(users);return;}throw new IllegalArgumentException("Unknown user");}
    void reset(String name,String value)throws Exception{
        JSONObject old=current(name);if(old==null)throw new IllegalArgumentException("Unknown user");JSONObject next=record(name,value,old.getString("role"));
        synchronized(STORE){JSONArray users=read();JSONObject present=find(users,name);if(present==null)throw new IllegalArgumentException("Unknown user");next.put("role",present.getString("role"));replace(users,name,next);}
    }
    void remove(String name)throws Exception{synchronized(STORE){JSONArray users=read(),next=new JSONArray();int admins=0;boolean found=false;for(int i=0;i<users.length();i++){JSONObject row=users.getJSONObject(i);if(row.getString("username").equals(name)){found=true;continue;}next.put(row);if(row.getString("role").equals("admin"))admins++;}if(!found||admins==0)throw new IllegalArgumentException("Keep at least one administrator");save(next);}}
    JSONArray users()throws Exception{synchronized(STORE){JSONArray result=new JSONArray(),users=read();for(int i=0;i<users.length();i++){JSONObject user=users.getJSONObject(i);result.put(new JSONObject().put("username",user.getString("username")).put("role",user.getString("role")));}return result;}}
    private String issue(JSONObject user)throws Exception{
        if(closed)throw new Denied(401);String token=CompanionServer.token();while(sessions.size()>=16)sessions.remove(sessions.keySet().iterator().next());sessions.put(token,new Session(user.getString("username"),user.getString("version")));return token;
    }
    synchronized String login(String name,String value)throws Exception{
        long now=SystemClock.elapsedRealtime();if(closed)throw new Denied(401);if(now<blockedUntil)throw new Denied(429);
        if(!username(name)||value==null||value.getBytes(StandardCharsets.UTF_8).length>72){failed(now);throw new Denied(401);}
        JSONObject user=current(name);boolean valid=matches(user,value);JSONObject latest=current(name);
        if(!valid||latest==null||!user.getString("version").equals(latest.getString("version"))){failed(SystemClock.elapsedRealtime());throw new Denied(401);}
        failures=0;return issue(latest);
    }
    private void failed(long now){if(++failures>=5){blockedUntil=now+60000;failures=0;}}
    synchronized JSONObject account(String authorization)throws Exception{
        if(closed||authorization==null||!authorization.startsWith("Bearer "))return null;
        String key=authorization.substring(7);Session session=sessions.get(key);if(session==null)return null;JSONObject user=current(session.username);
        if(SystemClock.elapsedRealtime()>=session.until||user==null||!session.version.equals(user.getString("version"))){sessions.remove(key);return null;}
        return new JSONObject().put("username",session.username).put("role",user.getString("role"));
    }
    synchronized String change(String authorization,String current,String next)throws Exception{
        if(SystemClock.elapsedRealtime()<blockedUntil)throw new Denied(429);JSONObject account=account(authorization);if(account==null)throw new Denied(401);password(next);
        String name=account.getString("username");JSONObject old=current(name);
        if(!matches(old,current)){failed(SystemClock.elapsedRealtime());throw new Denied(401);}
        JSONObject replacement=record(name,next,old.getString("role"));
        synchronized(STORE){JSONArray users=read();JSONObject present=find(users,name);if(present==null||!present.getString("version").equals(old.getString("version")))throw new Denied(401);replace(users,name,replacement);}
        failures=0;return issue(replacement);
    }
    synchronized void logout(String authorization){if(authorization!=null&&authorization.startsWith("Bearer "))sessions.remove(authorization.substring(7));}
    // Do not wait behind a password computation on Android's UI thread during service teardown.
    void clear(){closed=true;}
}
