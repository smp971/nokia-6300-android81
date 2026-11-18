package ro.leo.visual;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import org.json.*;
import java.util.HashSet;

/** Shared with the old text reader, serialized in the main-process provider. */
final class BrowserStore {
    static final Uri URI=Uri.parse("content://ro.leo.visual.browser");
    static Bundle request(Context c,String method,String arg,Bundle data){
        Bundle result=c.getContentResolver().call(URI,method,arg,data);
        if(result==null)throw new IllegalStateException("Browser state unavailable");return result;
    }
    static Bundle snapshot(Context c){return request(c,"snapshot",null,null);}
    static JSONArray records(Context c,String key){return clean(snapshot(c).getString(key,"[]"),key.equals("bookmarks")?30:50);}
    static JSONArray clean(String value,int limit){
        JSONArray out=new JSONArray();HashSet<String> seen=new HashSet<String>();
        if(value==null||value.length()>262144)return out;
        try{JSONArray all=new JSONArray(value);for(int i=0;i<all.length()&&out.length()<limit;i++){
            JSONObject item=all.optJSONObject(i);if(item==null)continue;String url=item.optString("url","");
            try{BrowserRules.checked(url);if(seen.add(url))out.put(new JSONObject().put("url",url).put("title",title(item.optString("title"),url)));}catch(Exception invalid){}
        }}catch(JSONException invalid){}return out;
    }
    static String title(String title,String url){String s=title==null?"":title.replaceAll("[\\p{Cntrl}]+"," ").trim();if(s.isEmpty())s=url;return s.substring(0,Math.min(160,s.length()));}
    static void remember(Context c,String key,String url,String title){Bundle b=new Bundle();b.putString("url",url);b.putString("title",title);request(c,"remember",key,b);}
    static void clear(Context c,String key){request(c,"clear",key,null);}
    static void remove(Context c,String key,String url){Bundle b=new Bundle();b.putString("url",url);request(c,"remove",key,b);}
    static void setting(Context c,String key,int value){Bundle b=new Bundle();b.putInt("value",value);request(c,"setting",key,b);}
    static void resume(Context c,String url,String title,int x,int y){Bundle b=new Bundle();b.putString("url",url);b.putString("title",title);b.putInt("x",Math.max(0,x));b.putInt("y",Math.max(0,y));request(c,"resume",null,b);}
}
