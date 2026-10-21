package ro.leo.visual;
import android.content.Context;import org.json.*;import java.util.*;
final class SmsTemplates {
 static final String[] DEFAULTS={"Te sun mai târziu.","Sunt pe drum.","Ajung în 10 minute.","Mulțumesc!","Nu pot vorbi acum. Scrie-mi, te rog."};
 static ArrayList<String> read(Context c){ArrayList<String> out=new ArrayList<String>();String raw=c.getSharedPreferences("sms-templates",0).getString("items",null);if(raw==null)return new ArrayList<String>(Arrays.asList(DEFAULTS));try{JSONArray a=new JSONArray(raw);for(int i=0;i<a.length()&&out.size()<20;i++){String s=a.getString(i);if(!s.trim().isEmpty()&&s.length()<=240)out.add(s);}}catch(JSONException ignored){}return out;}
 static void save(Context c,List<String> items){if(items.size()>20)throw new IllegalArgumentException();for(String s:items)if(s.trim().isEmpty()||s.length()>240)throw new IllegalArgumentException();c.getSharedPreferences("sms-templates",0).edit().putString("items",new JSONArray(items).toString()).apply();}
 static String insert(String body,String template,int cursor){int at=Math.max(0,Math.min(cursor,body.length()));return body.substring(0,at)+template+body.substring(at);}
}
