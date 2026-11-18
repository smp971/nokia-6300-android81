package ro.leo.visual;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import org.json.*;

/** Private single writer for the legacy browser preference schema. */
public final class BrowserStateProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    private boolean list(String key){return "bookmarks".equals(key)||"history".equals(key);}
    public synchronized Bundle call(String method,String key,Bundle data){
        if(Binder.getCallingUid()!=android.os.Process.myUid())throw new SecurityException("Own UID only");
        SharedPreferences p=getContext().getSharedPreferences("browser",Context.MODE_MULTI_PROCESS);
        Bundle result=new Bundle();
        if("snapshot".equals(method)){
            result.putString("bookmarks",BrowserStore.clean(p.getString("bookmarks","[]"),30).toString());result.putString("history",BrowserStore.clean(p.getString("history","[]"),50).toString());
            result.putString("last-url",p.getString("last-url",""));result.putString("last-title",p.getString("last-title",""));
            for(String setting:new String[]{"last-x","last-y","cursor-speed","font"})result.putInt(setting,p.getInt(setting,"font".equals(setting)?17:"cursor-speed".equals(setting)?1:0));return result;
        }
        SharedPreferences.Editor edit=p.edit();
        if("setting".equals(method)){
            int value=data.getInt("value");if(!("cursor-speed".equals(key)&&value>=0&&value<=2)&&!("font".equals(key)&&value>=14&&value<=26))throw new IllegalArgumentException();edit.putInt(key,value);
        }else if("clear".equals(method)&&list(key))edit.remove(key);
        else if("resume".equals(method)){
            String url=data.getString("url");try{BrowserRules.checked(url);}catch(Exception e){throw new IllegalArgumentException("Invalid URL");}
            edit.putString("last-url",url).putString("last-title",BrowserStore.title(data.getString("title"),url)).putInt("last-x",data.getInt("x")).putInt("last-y",data.getInt("y"));
        }else if(("remember".equals(method)||"remove".equals(method))&&list(key)){
            String url=data.getString("url");try{BrowserRules.checked(url);}catch(Exception e){throw new IllegalArgumentException("Invalid URL");}
            int limit="bookmarks".equals(key)?30:50;JSONArray old=BrowserStore.clean(p.getString(key,"[]"),limit),next=new JSONArray();
            if("remember".equals(method)&&old.length()>0&&url.equals(old.optJSONObject(0).optString("url"))&&BrowserStore.title(data.getString("title"),url).equals(old.optJSONObject(0).optString("title"))){result.putBoolean("ok",true);return result;}
            try{
                if("remember".equals(method))next.put(new JSONObject().put("url",url).put("title",BrowserStore.title(data.getString("title"),url)));
                for(int i=0;i<old.length()&&next.length()<limit;i++){JSONObject item=old.getJSONObject(i);if(!url.equals(item.optString("url")))next.put(item);}
            }catch(JSONException impossible){throw new IllegalArgumentException(impossible);}
            edit.putString(key,next.toString());
        }else throw new IllegalArgumentException("Unknown browser operation");
        if(!edit.commit())throw new IllegalStateException("Browser save failed");result.putBoolean("ok",true);return result;
    }
    public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new UnsupportedOperationException();}
    public String getType(Uri u){return null;}public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
