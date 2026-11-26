package ro.leo.visual;

import android.content.Context;
import android.graphics.Point;
import java.io.*;
import java.util.Map;
import org.json.*;

/** Authenticated private routes kept separate from assets, files and presentation settings. */
final class CompanionControl {
    final Context context;final CompanionCommunication phone;private long lastScreen;
    CompanionControl(Context c){context=c;phone=new CompanionCommunication(c);}
    synchronized boolean frameAllowed(){long now=android.os.SystemClock.elapsedRealtime();if(now-lastScreen<700)return false;lastScreen=now;return true;}
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
                    if(!frameAllowed()){CompanionServer.reply(out,429,"text/plain","Frame rate limited");return true;}
                    CompanionServer.respond(out,200,"image/png",CompanionRemote.screen(context));return true;
                }
                if(path.equals("/api/remote/info")&&method.equals("GET")){Point p=CompanionRemote.size(context);result=new JSONObject().put("width",p.x).put("height",p.y).put("interactive",((android.os.PowerManager)context.getSystemService(Context.POWER_SERVICE)).isInteractive()).put("rootAvailable",new File("/system/bin/leo-su").canExecute());}
                else if(method.equals("POST")){
                    JSONObject data=CompanionServer.json(in,headers);
                    if(path.equals("/api/remote/key"))result=CompanionRemote.key(context,data);
                    else if(path.equals("/api/remote/tap"))result=CompanionRemote.tap(context,data);
                    else if(path.equals("/api/remote/text"))result=CompanionRemote.text(context,data);
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
    void close(){phone.clear();}
}
