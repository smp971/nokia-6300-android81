package ro.leo.visual;

import android.content.Context;
import android.graphics.Point;
import java.io.*;
import java.util.Map;
import org.json.*;

/** Authenticated private routes kept separate from assets, files and presentation settings. */
final class CompanionControl {
    final Context context;final CompanionCommunication phone;final CompanionInputSession input;
    private long captured;private byte[] frame;private String digest;private boolean fastCapture=true;
    CompanionControl(Context c){context=c;phone=new CompanionCommunication(c);input=new CompanionInputSession(c);}
    private synchronized void screen(Map<String,String> headers,OutputStream out)throws Exception {
        long now=android.os.SystemClock.elapsedRealtime(),start=now;
        if(frame==null||now-captured>=200){byte[] next=null;if(fastCapture)try{next=input.capture();}catch(UnsupportedOperationException unsupported){fastCapture=false;}
            if(next==null)next=CompanionRemote.screen(context);frame=next;captured=android.os.SystemClock.elapsedRealtime();byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(frame);StringBuilder value=new StringBuilder();for(byte b:hash)value.append(String.format(java.util.Locale.ROOT,"%02x",b&255));digest="\""+value+"\"";}
        Point size=CompanionRemote.size(context);boolean interactive=((android.os.PowerManager)context.getSystemService(Context.POWER_SERVICE)).isInteractive();
        String extra="ETag: "+digest+"\r\nX-KeyShell-Width: "+size.x+"\r\nX-KeyShell-Height: "+size.y+"\r\nX-KeyShell-Interactive: "+interactive+"\r\nX-KeyShell-Capture-Ms: "+(android.os.SystemClock.elapsedRealtime()-start)+"\r\n";
        if(digest.equals(headers.get("if-none-match")))CompanionServer.head(out,304,"image/png",0,extra);
        else{CompanionServer.head(out,200,"image/png",frame.length,extra);out.write(frame);}out.flush();
    }
    boolean serve(String method,String path,String session,JSONObject account,InputStream in,Map<String,String> headers,OutputStream out)throws Exception {
        boolean remote=path.startsWith("/api/remote/"),communication=path.startsWith("/api/phone/");
        if(!remote&&!communication)return false;
        if(!account.optString("role").equals("admin")||!CompanionSettings.feature(context,remote?"remote":"communication")){
            CompanionServer.reply(out,403,"text/plain","Feature or role denied");return true;
        }
        try{
            JSONObject result=null;
            if(remote){
                if(path.equals("/api/remote/screen")&&method.equals("GET")){
                    screen(headers,out);return true;
                }
                if(path.equals("/api/remote/info")&&method.equals("GET")){Point p=CompanionRemote.size(context);result=new JSONObject().put("width",p.x).put("height",p.y).put("interactive",((android.os.PowerManager)context.getSystemService(Context.POWER_SERVICE)).isInteractive()).put("rootAvailable",new File("/system/bin/leo-su").canExecute());}
                else if(method.equals("POST")){
                    JSONObject data=CompanionServer.json(in,headers);
                    if(path.equals("/api/remote/key"))result=CompanionRemote.key(context,data,input);
                    else if(path.equals("/api/remote/tap"))result=CompanionRemote.tap(context,data,input);
                    else if(path.equals("/api/remote/text"))result=CompanionRemote.text(context,data,input);
                    else if(path.equals("/api/remote/launch"))result=CompanionRemote.launch(context,data);
                }
            }else if(method.equals("GET")){
                if(path.equals("/api/phone/state"))result=CompanionCommunication.state(context);
                else if(path.equals("/api/phone/contacts"))result=new JSONObject().put("rows",CompanionCommunication.contacts(context));
                else if(path.equals("/api/phone/history"))result=new JSONObject().put("rows",CompanionCommunication.history(context));
                else if(path.equals("/api/phone/messages"))result=new JSONObject().put("rows",CompanionCommunication.messages(context));
            }else if(method.equals("POST")){
                JSONObject data=CompanionServer.json(in,headers);
                if(path.equals("/api/phone/prepare"))result=phone.prepare(session,data);
                else if(path.equals("/api/phone/confirm"))result=phone.confirm(session,data);
                else if(path.equals("/api/phone/action"))result=CompanionCommunication.callAction(data);
            }
            if(result==null)CompanionServer.reply(out,404,"text/plain","Unknown control endpoint");
            else CompanionServer.reply(out,200,"application/json",result.toString());
        }catch(JSONException|IllegalArgumentException invalid){CompanionServer.reply(out,400,"text/plain","Invalid control request");}
        catch(SecurityException denied){CompanionServer.reply(out,403,"text/plain","Android permission unavailable");}
        catch(IllegalStateException busy){CompanionServer.reply(out,409,"text/plain","Operation unavailable, expired or already consumed; refresh state before retrying");}
        catch(Exception unavailable){CompanionServer.reply(out,503,"text/plain","Phone control unavailable");}
        return true;
    }
    void close(){phone.clear();input.close();synchronized(this){frame=null;digest=null;}}
}
