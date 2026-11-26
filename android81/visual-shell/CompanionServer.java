package ro.leo.visual;
import android.content.*;import java.net.*;import java.io.*;import java.security.*;import java.util.*;import java.util.concurrent.*;import org.json.*;
/** Small authenticated LAN server; only its own exchange folder is addressable. */
final class CompanionServer implements java.io.Closeable {
 static final int LIMIT=16777216;final Context context;final ServerSocket server;final String token;final String scheme;final CompanionAuth auth;final Set<Socket> sockets=Collections.newSetFromMap(new ConcurrentHashMap<Socket,Boolean>());final ThreadPoolExecutor clients=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(4));volatile boolean running=true;
 CompanionServer(Context c,InetAddress address,int port,String secret)throws IOException{this(c,address,port,secret,false);}
 CompanionServer(Context c,InetAddress address,int port,String secret,boolean secure)throws IOException{this(c,address,port,secret,null,secure);}
 CompanionServer(Context c,InetAddress address,int port,CompanionAuth auth)throws IOException{this(c,address,port,null,auth,true);}
 private CompanionServer(Context c,InetAddress address,int port,String secret,CompanionAuth access,boolean secure)throws IOException{
  context=c.getApplicationContext();token=secret;auth=access;scheme=secure?"https":"http";
  if(!address.isLoopbackAddress()&&(!secure||access==null))throw new IOException("Password HTTPS required outside loopback");
  try{server=secure?CompanionTls.socket():new ServerSocket();}catch(Exception e){throw new IOException("TLS identity unavailable",e);}
  try{server.setReuseAddress(true);server.bind(new InetSocketAddress(address,port),4);}catch(IOException e){server.close();throw e;}
 }
 static String token(){byte[] b=new byte[16];new SecureRandom().nextBytes(b);StringBuilder out=new StringBuilder();for(byte value:b)out.append(String.format(Locale.ROOT,"%02x",value&255));return out.toString();}
 static File folder(Context c){File f=new File(c.getFilesDir(),"documents/Companion");f.mkdirs();return f;}
 static boolean name(String value){return value!=null&&value.matches("[A-Za-z0-9][A-Za-z0-9._ -]{0,79}")&&!value.contains("..");}
 static boolean authenticated(String provided,String expected){return provided!=null&&MessageDigest.isEqual(provided.getBytes(java.nio.charset.StandardCharsets.UTF_8),("Bearer "+expected).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 void start(){new Thread(new Runnable(){public void run(){while(running)try{final Socket socket=server.accept();synchronized(CompanionServer.this){if(!running){socket.close();continue;}sockets.add(socket);}try{clients.execute(new Runnable(){public void run(){try{serve(socket);}finally{sockets.remove(socket);}}});}catch(RejectedExecutionException e){sockets.remove(socket);socket.close();}}catch(IOException e){if(running)running=false;}}},"LeoCompanionAccept").start();}
 static String line(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();int b;while((b=in.read())!=-1){if(b==10)break;if(out.size()>=4096)throw new IOException("Line too long");if(b!=13)out.write(b);}if(b==-1&&out.size()==0)throw new EOFException();return out.toString("US-ASCII");}
 void serve(Socket socket){try(Socket s=socket){s.setSoTimeout(5000);InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();String[] request=line(in).split(" ");if(request.length!=3||!request[2].equals("HTTP/1.1")){reply(out,400,"text/plain","Bad request");return;}Map<String,String> headers=new HashMap<String,String>();int size=0;for(int k=0;k<32;k++){String h=line(in);size+=h.length();if(size>16384)throw new IOException();if(h.isEmpty())break;int split=h.indexOf(':');if(split<1)throw new IOException();String key=h.substring(0,split).trim().toLowerCase(Locale.ROOT);if(headers.containsKey(key))throw new IOException();headers.put(key,h.substring(split+1).trim());if(k==31)throw new IOException();}String host=headers.get("host"),own=server.getInetAddress().getHostAddress()+":"+server.getLocalPort();if(!own.equals(host)){reply(out,403,"text/plain","Host rejected");return;}String origin=headers.get("origin");if(origin!=null&&!origin.equals(scheme+"://"+own)){reply(out,403,"text/plain","Origin rejected");return;}String method=request[0],path=request[1];if(headers.containsKey("transfer-encoding")){reply(out,400,"text/plain","Content-Length required");return;}
   if(method.equals("GET")){
    if(path.equals("/")||path.equals("/index.html")){asset(out,"webui/index.html","text/html; charset=utf-8");return;}
    if(path.equals("/style.css")||path.equals("/app.js")||path.equals("/catalog.js")){asset(out,"webui"+path,path.endsWith(".css")?"text/css; charset=utf-8":"application/javascript; charset=utf-8");return;}
    if(path.matches("/catalog-icons/gaia/(phone|contacts|messages|clock|gallery|music|settings|calendar|video|email|fm|camera)\\.png")){asset(out,path.substring(1),"image/png");return;}
   }
   if(method.equals("GET")&&path.equals("/api/info")){reply(out,200,"application/json",new JSONObject().put("theme",CompanionSettings.theme(context)).toString());return;}
   if(method.equals("POST")&&path.equals("/api/login")&&auth!=null){
    try{JSONObject data=json(in,headers);CompanionModel.exact(data,"username","password");String key=auth.login(string(data,"username"),string(data,"password"));reply(out,200,"application/json",new JSONObject().put("token",key).toString());}
    catch(CompanionAuth.Denied denied){reply(out,denied.status,"text/plain","Sign-in refused");}catch(JSONException|IllegalArgumentException invalid){reply(out,400,"text/plain","Invalid sign-in");}return;
   }
   String authorization=headers.get("authorization");JSONObject account=auth==null?(authenticated(authorization,token)?new JSONObject().put("username","fixture").put("role","admin"):null):auth.account(authorization);
   if(!running||account==null){reply(out,401,"text/plain","Authentication required");return;}
   boolean administrator=account.getString("role").equals("admin");
   if(path.equals("/api/logout")&&method.equals("POST")){if(auth!=null)auth.logout(authorization);reply(out,200,"application/json","{}");return;}
   if(path.equals("/api/password")&&method.equals("POST")&&auth!=null){
    try{JSONObject data=json(in,headers);CompanionModel.exact(data,"currentPassword","newPassword");String key=auth.change(authorization,string(data,"currentPassword"),string(data,"newPassword"));reply(out,200,"application/json",new JSONObject().put("token",key).toString());}
    catch(CompanionAuth.Denied denied){reply(out,denied.status,"text/plain","Password change refused");}catch(JSONException|IllegalArgumentException invalid){reply(out,400,"text/plain","Invalid password");}return;
   }
   if((!method.equals("GET")&&!administrator)||(path.startsWith("/api/admin")&&!administrator)||
      (path.equals("/api/appearance")&&!CompanionSettings.feature(context,"appearance"))||
      (path.equals("/api/layout")&&!CompanionSettings.feature(context,"layout"))||
      ((path.equals("/api/files")||path.startsWith("/api/file/")||path.equals("/api/upload"))&&!CompanionSettings.feature(context,"files"))){reply(out,403,"text/plain","Feature or role denied");return;}
   if(method.equals("GET")&&path.equals("/api/admin")&&auth!=null){reply(out,200,"application/json",new JSONObject().put("users",auth.users()).put("settings",CompanionSettings.snapshot(context)).toString());return;}
   if(method.equals("POST")&&path.startsWith("/api/admin/")&&auth!=null){
    try{JSONObject data=json(in,headers);
     if(path.equals("/api/admin/settings"))CompanionSettings.apply(context,data);
     else if(path.equals("/api/admin/users")){
      String action=string(data,"action"),name=string(data,"username");
      if(action.equals("add")){CompanionModel.exact(data,"action","username","password","role");auth.add(name,string(data,"password"),string(data,"role"));}
      else if(action.equals("password")){CompanionModel.exact(data,"action","username","password");auth.reset(name,string(data,"password"));}
      else if(action.equals("delete")){CompanionModel.exact(data,"action","username");auth.remove(name);}
      else throw new IllegalArgumentException();
     }else{reply(out,404,"text/plain","Unknown endpoint");return;}
     reply(out,200,"application/json","{\"saved\":true}");
    }catch(JSONException|IllegalArgumentException invalid){reply(out,400,"text/plain","Invalid administration settings");}return;
   }
   if(method.equals("GET")&&(path.equals("/api/status")||path.equals("/api/desktop"))){JSONObject model=CompanionModel.snapshot(context).put("account",account).put("webui",CompanionSettings.snapshot(context));reply(out,200,"application/json",(path.equals("/api/status")?model.getJSONObject("status"):model).toString());}
   else if(method.equals("POST")&&(path.equals("/api/appearance")||path.equals("/api/layout"))){
    try{long length=Long.parseLong(headers.get("content-length"));if(length<1||length>16384||!"application/json".equals(headers.get("content-type")))throw new IllegalArgumentException();
     byte[] bytes=new byte[(int)length];new DataInputStream(in).readFully(bytes);JSONObject values=new JSONObject(new String(bytes,"UTF-8"));
     if(!running)throw new IOException();if(path.equals("/api/layout"))CompanionModel.layout(context,values);else CompanionModel.appearance(context,values);reply(out,200,"application/json","{\"saved\":true}");
    }catch(JSONException|IllegalArgumentException e){reply(out,400,"text/plain","Invalid settings");}
   }else if(method.equals("GET")&&path.equals("/api/files")){JSONArray files=new JSONArray();File[] all=folder(context).listFiles();if(all!=null)for(File f:all)if(files.length()<64&&f.isFile()&&name(f.getName()))files.put(new JSONObject().put("name",f.getName()).put("bytes",f.length()));reply(out,200,"application/json",files.toString());}else if(method.equals("GET")&&path.startsWith("/api/file/")){String filename=URLDecoder.decode(path.substring(10),"UTF-8");if(!name(filename)){reply(out,400,"text/plain","Invalid filename");return;}File file=new File(folder(context),filename);if(!file.isFile()||file.length()>LIMIT){reply(out,404,"text/plain","Not found");return;}head(out,200,"application/octet-stream",file.length());try(InputStream data=new FileInputStream(file)){byte[] bytes=new byte[16384];int n;while((n=data.read(bytes))!=-1)out.write(bytes,0,n);}}else if(method.equals("POST")&&path.equals("/api/upload")){if(headers.containsKey("transfer-encoding"))throw new IOException();String filename=headers.get("x-leo-name");long length=Long.parseLong(headers.get("content-length"));if(!name(filename)||length<0||length>LIMIT||folder(context).getUsableSpace()<length+2097152){reply(out,413,"text/plain","Invalid name or size; maximum 16 MB");return;}File target=new File(folder(context),filename);if(target.exists()){reply(out,409,"text/plain","File already exists");return;}File stage=new File(folder(context),".upload-"+UUID.randomUUID());try{try(FileOutputStream file=new FileOutputStream(stage)){byte[] bytes=new byte[16384];long remaining=length;while(remaining>0){int n=in.read(bytes,0,(int)Math.min(bytes.length,remaining));if(n<0)throw new EOFException();file.write(bytes,0,n);remaining-=n;}file.getFD().sync();}synchronized(CompanionServer.class){if(target.exists()){reply(out,409,"text/plain","File already exists");return;}if(!running||!stage.renameTo(target))throw new IOException();}reply(out,201,"application/json","{\"uploaded\":true}");}finally{stage.delete();}}else reply(out,404,"text/plain","Unknown endpoint");out.flush();}catch(Exception ignored){}}
 static JSONObject json(InputStream in,Map<String,String> headers)throws Exception{
  long length;try{length=Long.parseLong(headers.get("content-length"));}catch(Exception e){throw new IllegalArgumentException();}
  if(length<1||length>16384||!"application/json".equals(headers.get("content-type")))throw new IllegalArgumentException();
  byte[] bytes=new byte[(int)length];new DataInputStream(in).readFully(bytes);return new JSONObject(new String(bytes,"UTF-8"));
 }
 static String string(JSONObject data,String key)throws Exception{Object value=data.get(key);if(!(value instanceof String))throw new IllegalArgumentException();return (String)value;}
 void asset(OutputStream out,String name,String type)throws IOException{
  try(InputStream in=context.getAssets().open(name);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
   byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(bytes.size()+n>262144)throw new IOException("Asset too large");bytes.write(buf,0,n);}respond(out,200,type,bytes.toByteArray());
  }
 }
 static void reply(OutputStream out,int status,String type,String body)throws IOException{respond(out,status,type,body.getBytes("UTF-8"));}
 static void respond(OutputStream out,int status,String type,byte[] body)throws IOException{head(out,status,type,body.length);out.write(body);out.flush();}
 static void head(OutputStream out,int status,String type,long bytes)throws IOException{String reason=status==200?"OK":status==201?"Created":"Request rejected";out.write(("HTTP/1.1 "+status+" "+reason+"\r\nContent-Type: "+type+"\r\nContent-Length: "+bytes+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'\r\n\r\n").getBytes("US-ASCII"));}
 public synchronized void close(){running=false;try{server.close();}catch(IOException ignored){}for(Socket socket:sockets)try{socket.close();}catch(IOException ignored){}clients.shutdownNow();}
}
